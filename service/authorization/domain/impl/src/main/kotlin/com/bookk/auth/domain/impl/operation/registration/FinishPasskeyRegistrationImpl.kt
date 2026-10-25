package com.bookk.auth.domain.impl.operation.registration

import com.bookk.auth.domain.api.authentication.entity.Authentication
import com.bookk.auth.domain.api.authentication.entity.FinishRegistrationRequest
import com.bookk.auth.domain.api.identification.entity.PasskeyCredential
import com.bookk.auth.domain.api.identification.entity.PasskeyCredential.CredentialDescriptor
import com.bookk.auth.domain.api.registration.operation.FinishPasskeyRegistration
import com.bookk.auth.domain.api.registration.operation.FinishPasskeyRegistration.Error.ChallengeWindowExpired
import com.bookk.auth.domain.api.registration.operation.FinishPasskeyRegistration.Error.VerificationFailed
import com.bookk.auth.domain.datasource.PassKeyDataSource
import com.bookk.auth.domain.impl.passkey.createRelyingParty
import com.bookk.auth.domain.repository.CacheableCredentialRepository
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.entity.recoverSuspendCatching
import com.bookk.core.toUUID
import com.yubico.webauthn.FinishRegistrationOptions
import com.yubico.webauthn.RegistrationResult
import com.yubico.webauthn.data.AuthenticatorAttestationResponse
import com.yubico.webauthn.data.ClientRegistrationExtensionOutputs
import com.yubico.webauthn.data.PublicKeyCredential
import com.yubico.webauthn.data.PublicKeyCredentialCreationOptions
import com.yubico.webauthn.exception.RegistrationFailedException
import kotlin.jvm.optionals.getOrElse
import kotlin.jvm.optionals.getOrNull
import kotlin.time.Clock
import kotlin.uuid.Uuid

private typealias PKS = PublicKeyCredential<AuthenticatorAttestationResponse, ClientRegistrationExtensionOutputs>

internal class FinishPasskeyRegistrationImpl(
    private val passKeyDS: PassKeyDataSource,
    private val credentialRepo: CacheableCredentialRepository,
    private val transactionManager: TransactionManager
) : FinishPasskeyRegistration {
    override suspend fun verifyRequest(request: FinishRegistrationRequest): Result<PasskeyCredential> {
        return transactionManager.transaction {
            val pkc = PublicKeyCredential.parseRegistrationResponseJson(request.publicKeyCredentialJson)
            val challengeJson = passKeyDS.getCachedChallenge(request.requestId) ?: throw ChallengeWindowExpired()
            val challenge = PublicKeyCredentialCreationOptions.fromJson(challengeJson)
            validateRegistrationChallenge(request, challenge, pkc).asPasskeyCredential(pkc, challenge)
        }.recoverSuspendCatching {
            when (it) {
                is RegistrationFailedException -> throw VerificationFailed()
                else -> throw it
            }
        }
    }

    override suspend fun attachOwner(ownerId: Uuid, passkey: PasskeyCredential) {
        val passkeyWithOwner = passkey.copy(authId = ownerId)
        passKeyDS.createPasskeyCredential(passkeyWithOwner)
    }

    private suspend fun validateRegistrationChallenge(
        request: FinishRegistrationRequest,
        challenge: PublicKeyCredentialCreationOptions,
        response: PKS
    ): RegistrationResult {
        passKeyDS.deleteCachedChallenge(request.requestId)
        cacheRepositoryData(response)
        return createRelyingParty(credentialRepo)
            .finishRegistration(
                FinishRegistrationOptions.builder()
                    .request(challenge)
                    .response(response)
                    .build()
            )
    }

    private suspend fun cacheRepositoryData(response: PKS) {
        credentialRepo.lookupAllCache(response.id)
    }

    //This is actually OptIn for not yet mature FIDO standards
    @Suppress("DEPRECATION")
    private fun RegistrationResult.asPasskeyCredential(pkc: PKS, challenge: PublicKeyCredentialCreationOptions): PasskeyCredential {
        return PasskeyCredential(
            id = Uuid.random(),
            authId = Uuid.random(),
            authInfo = Authentication(Uuid.random(), Uuid.random(), Uuid.random()), //Ignored
            name = challenge.user.displayName,
            credDescriptor = CredentialDescriptor(
                id = keyId.id.bytes,
                type = keyId.type.id,
                transports = keyId.transports.getOrNull()?.map { it.id }.orEmpty().toSet()
            ),
            publicKey = publicKeyCose.base64,
            signatureCount = signatureCount,
            isDiscoverable = isDiscoverable.getOrElse { false },
            isBackupEligible = isBackupEligible,
            isBackedUp = isBackedUp,
            attestationObject = pkc.response.attestationObject.bytes,
            clientData = pkc.response.clientDataJSON.bytes.decodeToString(),
            handle = challenge.user.id.bytes.toUUID(),
            createdAt = Clock.System.now(),
            lastUsedAt = Clock.System.now()
        )
    }
}