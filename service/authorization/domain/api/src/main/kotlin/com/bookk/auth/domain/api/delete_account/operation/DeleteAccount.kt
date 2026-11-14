package com.bookk.auth.domain.api.delete_account.operation

import com.bookk.auth.domain.api.error.AuthErrorCodes
import com.bookk.core.domain.entity.BusinessError
import io.ktor.http.HttpStatusCode
import kotlin.uuid.Uuid

interface DeleteAccount {

    suspend operator fun invoke(userId: Uuid, confirmationToken: String): Result<Unit>

    sealed interface Error {
        class ActionConfirmationInvalid :
            BusinessError(
                HttpStatusCode.Forbidden.value,
                AuthErrorCodes.ACTION_CONFIRMATION_INVALID,
                "A valid passkey action confirmation token is required",
            ),
            Error

        class InvalidCredentials :
            BusinessError(
                statusCode = HttpStatusCode.UnprocessableEntity.value,
                code = AuthErrorCodes.VERIFICATION_FAILED,
                message = "Invalid credentials",
            ),
            Error
    }
}
