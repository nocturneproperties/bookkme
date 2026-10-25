package com.bookk.auth.domain.impl.operation.authentication

import com.bookk.auth.domain.api.authentication.entity.AssertionStartResponse
import com.bookk.auth.domain.api.authentication.operation.StartAssertion
import com.bookk.auth.domain.datasource.PassKeyDataSource
import com.bookk.auth.domain.impl.passkey.createRelyingParty
import com.bookk.auth.domain.repository.CacheableCredentialRepository
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.yubico.webauthn.StartAssertionOptions
import com.yubico.webauthn.data.UserVerificationRequirement
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

internal class StartAssertionImpl(
    private val passKeyDataSource: PassKeyDataSource,
    private val credentialsRepository: CacheableCredentialRepository,
    private val transactionManager: TransactionManager
) : StartAssertion {
    override suspend fun invoke(): Result<AssertionStartResponse> = transactionManager.transaction {
        val requestUUID = Uuid.random().toString()
        val request = createRelyingParty(credentialsRepository)
            .startAssertion(
                StartAssertionOptions
                    .builder()
                    .userVerification(UserVerificationRequirement.REQUIRED)
                    .timeout(5.minutes.inWholeMilliseconds)
                    .build()
            )
        passKeyDataSource.saveChallengeToCache(requestUUID, request.toJson())
        AssertionStartResponse(
            requestId = requestUUID,
            challengeJson = request.toCredentialsGetJson(),
            challenge = request.publicKeyCredentialRequestOptions.challenge.base64Url
        )
    }
}