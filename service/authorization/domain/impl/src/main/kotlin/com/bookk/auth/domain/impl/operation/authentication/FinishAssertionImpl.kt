package com.bookk.auth.domain.impl.operation.authentication

import com.bookk.auth.domain.api.authentication.entity.FinishAssertionRequest
import com.bookk.auth.domain.api.authentication.operation.FinishAssertion
import com.bookk.auth.domain.api.authentication.operation.FinishAssertion.Error
import com.bookk.auth.domain.api.identification.entity.PasskeyCredential
import com.bookk.auth.domain.datasource.PassKeyDataSource
import com.bookk.auth.domain.impl.passkey.createRelyingParty
import com.bookk.auth.domain.repository.CacheableCredentialRepository
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.transactionOrThrow
import com.bookk.core.domain.entity.runSuspendCatching
import com.bookk.core.toUUID
import com.yubico.webauthn.AssertionRequest
import com.yubico.webauthn.AssertionResult
import com.yubico.webauthn.FinishAssertionOptions
import com.yubico.webauthn.data.AuthenticatorAssertionResponse
import com.yubico.webauthn.data.ClientAssertionExtensionOutputs
import com.yubico.webauthn.data.PublicKeyCredential
import com.yubico.webauthn.exception.AssertionFailedException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.jvm.optionals.getOrNull
import com.yubico.webauthn.data.ByteArray as YubicoByteArray

private typealias AssertionCredential =
    PublicKeyCredential<AuthenticatorAssertionResponse, ClientAssertionExtensionOutputs>

internal class FinishAssertionImpl(
    private val passKeyDataSource: PassKeyDataSource,
    private val credentialRepository: CacheableCredentialRepository,
    private val transactionManager: TransactionManager,
) : FinishAssertion {
    override suspend fun invoke(request: FinishAssertionRequest): Result<PasskeyCredential> = runSuspendCatching {
        val cachedRequest =
            passKeyDataSource.takeCachedChallenge(request.requestId) ?: throw Error.ChallengeWindowExpired()
        val challenge = AssertionRequest.fromJson(cachedRequest)
        val response = parseAssertion(request.publicKeyCredentialJson)
        val userHandle = response.response.userHandle.getOrNull() ?: throw Error.PasskeyOwnerNotFound()
        transactionManager.transactionOrThrow {
            cacheRepositoryData(response.id, userHandle)
            if (!isRegistered(response.id, userHandle)) throw Error.PasskeyUnknown()
            val result: AssertionResult = runCatching {
                createRelyingParty(credentialRepository)
                    .finishAssertion(
                        FinishAssertionOptions.builder()
                            .request(challenge)
                            .response(response)
                            .build(),
                    )
            }.recover {
                throw when (it) {
                    is AssertionFailedException -> Error.PasskeyOwnerNotFound()
                    else -> Error.VerificationFailed()
                }
            }.getOrThrow()
            if (result.isSuccess) {
                val credentials = passKeyDataSource.getCredentialBy(
                    userHandle = result.credential.userHandle.bytes.toUUID(),
                    credentialId = result.credential.credentialId.bytes,
                )
                if (credentials == null) throw Error.PasskeyUnknown()
                passKeyDataSource.markAsUsed(credentials.id)
                credentials
            } else {
                throw Error.PasskeyOwnerNotFound()
            }
        }
    }

    private fun isRegistered(credentialId: YubicoByteArray, userHandle: YubicoByteArray): Boolean {
        return runCatching { credentialRepository.lookup(credentialId, userHandle).isPresent }
            .getOrElse { throw Error.VerificationFailed() }
    }

    private fun parseAssertion(json: String): AssertionCredential {
        return runCatching { PublicKeyCredential.parseAssertionResponseJson(json) }
            .getOrElse { throw Error.VerificationFailed() }
    }

    private suspend fun cacheRepositoryData(credentialId: YubicoByteArray, userHandle: YubicoByteArray) {
        coroutineScope {
            listOf(
                async { credentialRepository.cacheUsername(handle = userHandle) },
                async { credentialRepository.lookupCache(credentialId, userHandle) },
            )
        }.awaitAll()
    }
}
