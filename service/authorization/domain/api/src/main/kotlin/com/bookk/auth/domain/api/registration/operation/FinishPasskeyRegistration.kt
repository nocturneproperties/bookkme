package com.bookk.auth.domain.api.registration.operation

import com.bookk.auth.domain.api.authentication.entity.FinishRegistrationRequest
import com.bookk.auth.domain.api.error.AuthErrorCodes
import com.bookk.auth.domain.api.identification.entity.PasskeyCredential
import com.bookk.core.domain.entity.BusinessError
import io.ktor.http.HttpStatusCode
import kotlin.uuid.Uuid

interface FinishPasskeyRegistration {
    suspend fun verifyRequest(request: FinishRegistrationRequest): Result<PasskeyCredential>
    suspend fun attachOwner(ownerId: Uuid, passkey: PasskeyCredential)

    sealed interface Error {
        class ChallengeWindowExpired : BusinessError(
            statusCode = HttpStatusCode.UnprocessableEntity.value,
            code = AuthErrorCodes.CHALLENGE_WINDOW_EXPIRED,
            message = "Challenge window expired"
        ), Error

        class VerificationFailed : BusinessError(
            statusCode = HttpStatusCode.UnprocessableEntity.value,
            code = AuthErrorCodes.VERIFICATION_FAILED,
            message = "Passkey verification failed"
        ), Error
    }
}