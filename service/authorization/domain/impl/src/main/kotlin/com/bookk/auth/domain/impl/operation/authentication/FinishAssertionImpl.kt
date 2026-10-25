package com.bookk.auth.domain.impl.operation.authentication

import com.bookk.auth.domain.api.authentication.entity.FinishAssertionRequest
import com.bookk.auth.domain.api.authentication.operation.FinishAssertion
import com.bookk.auth.domain.api.authentication.operation.FinishAssertion.Error
import com.bookk.auth.domain.api.identification.entity.PasskeyCredential
import com.bookk.auth.domain.datasource.PassKeyDataSource
import com.bookk.auth.domain.impl.passkey.createRelyingParty
import com.bookk.auth.domain.repository.CacheableCredentialRepository
import com.bookk.core.domain.datasource.transaction.TransactionManager
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

internal class FinishAssertionImpl(
    private val passKeyDataSource: PassKeyDataSource,
    private val credentialRepository: CacheableCredentialRepository,
    private val transactionManager: TransactionManager
) : FinishAssertion {
    override suspend fun invoke(request: FinishAssertionRequest): Result<PasskeyCredential> = runSuspendCatching {
        val cachedRequest = passKeyDataSource.getCachedChallenge(request.requestId) ?: throw Error.ChallengeWindowExpired()
        val challenge = AssertionRequest.fromJson(cachedRequest)
        val response = PublicKeyCredential.parseAssertionResponseJson(request.publicKeyCredentialJson)
        passKeyDataSource.deleteCachedChallenge(request.requestId)
        return transactionManager.transaction {
            cacheRepositoryData(response)
            val result: AssertionResult = runCatching {
                createRelyingParty(credentialRepository)
                    .finishAssertion(
                        FinishAssertionOptions.builder()
                            .request(challenge)
                            .response(response)
                            .build()
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
                    credentialId = result.credential.credentialId.bytes
                )
                if (credentials == null) throw Error.PasskeyOwnerNotFound()
                passKeyDataSource.markAsUsed(credentials.id)
                credentials
            } else {
                throw Error.PasskeyOwnerNotFound()
            }
        }
    }

    private suspend fun cacheRepositoryData(
        response: PublicKeyCredential<AuthenticatorAssertionResponse, ClientAssertionExtensionOutputs>
    ) {
        coroutineScope {
            listOf(
                async { credentialRepository.cacheUsername(handle = response.response.userHandle.get()) },
                async { credentialRepository.lookupCache(response.id, response.response.userHandle.get()) }
            )
        }.awaitAll()
    }
}