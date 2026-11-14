package com.bookk.auth.domain.api.authentication.operation

import com.bookk.auth.domain.api.authentication.entity.FinishAssertionRequest
import com.bookk.auth.domain.api.error.AuthErrorCodes
import com.bookk.auth.domain.api.identification.entity.PasskeyCredential
import com.bookk.core.domain.entity.BusinessError
import io.ktor.http.HttpStatusCode
import kotlin.uuid.Uuid

interface FinishAssertion {

    suspend operator fun invoke(request: FinishAssertionRequest): Result<PasskeyCredential>

    sealed interface Error {
        class PasskeyOwnerNotFound :
            BusinessError(
                HttpStatusCode.UnprocessableEntity.value,
                AuthErrorCodes.PASSKEY_OWNER_NOT_FOUND,
                "Passkey owner not found",
            ),
            Error
        class ChallengeWindowExpired :
            BusinessError(
                HttpStatusCode.UnprocessableEntity.value,
                AuthErrorCodes.CHALLENGE_WINDOW_EXPIRED,
                "Challenge window expired",
            ),
            Error
        class VerificationFailed :
            BusinessError(
                HttpStatusCode.UnprocessableEntity.value,
                AuthErrorCodes.VERIFICATION_FAILED,
                "Passkey verification failed",
            ),
            Error
        class PasskeyNotOwned :
            BusinessError(
                HttpStatusCode.UnprocessableEntity.value,
                AuthErrorCodes.PASSKEY_NOT_OWNED,
                "Passkey belongs to another account",
            ),
            Error
    }
}

suspend fun FinishAssertion.finishOwnedBy(
    userId: Uuid,
    request: FinishAssertionRequest,
): Result<PasskeyCredential> = invoke(request).mapCatching { credential ->
    if (credential.authInfo.userId != userId) throw FinishAssertion.Error.PasskeyNotOwned()
    credential
}
