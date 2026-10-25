package com.bookk.auth.domain.impl.operation.registration

import com.bookk.auth.domain.api.registration.entity.RegistrationChallengeResponse
import com.bookk.auth.domain.api.registration.operation.StartPasskeyRegistration
import com.bookk.auth.domain.datasource.PassKeyDataSource
import com.bookk.auth.domain.impl.passkey.createRelyingParty
import com.bookk.auth.domain.repository.CacheableCredentialRepository
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.yubico.webauthn.StartRegistrationOptions
import com.yubico.webauthn.data.AuthenticatorSelectionCriteria
import com.yubico.webauthn.data.PublicKeyCredentialCreationOptions
import com.yubico.webauthn.data.ResidentKeyRequirement
import com.yubico.webauthn.data.UserIdentity
import kotlin.uuid.Uuid
import com.yubico.webauthn.data.ByteArray as YubicoByteArray

internal class StartPasskeyRegistrationImpl(
    private val passKeyDataSource: PassKeyDataSource,
    private val credentialRepository: CacheableCredentialRepository,
    private val transactionManager: TransactionManager
) : StartPasskeyRegistration {

    override suspend fun invoke(userHandle: Uuid, passkeyDisplayName: String): Result<RegistrationChallengeResponse> {
        return transactionManager.transaction {
            val requestId = Uuid.random().toString()
            val challenge = createChallenge(requestId, passkeyDisplayName, YubicoByteArray(userHandle.toByteArray()))
            passKeyDataSource.saveChallengeToCache(requestId, challenge.toJson())
            RegistrationChallengeResponse(
                requestId = requestId,
                challenge = challenge.challenge.base64Url,
                challengeJson = challenge.toCredentialsCreateJson(),
                userHandle = challenge.user.id.base64Url,
                displayName = passkeyDisplayName
            )
        }
    }

    private suspend fun createChallenge(
        userName: String,
        displayName: String,
        handle: YubicoByteArray
    ): PublicKeyCredentialCreationOptions {
        credentialRepository.cacheCredentialIdsForUsername(userName)
        return createRelyingParty(credentialRepository).startRegistration(
            StartRegistrationOptions.builder()
                .user(
                    UserIdentity.builder()
                        .name(userName)
                        .displayName(displayName)
                        .id(handle)
                        .build()
                )
                .authenticatorSelection(
                    AuthenticatorSelectionCriteria.builder()
                        .residentKey(ResidentKeyRequirement.REQUIRED)
                        .build()
                )
                .build()
        )
    }
}