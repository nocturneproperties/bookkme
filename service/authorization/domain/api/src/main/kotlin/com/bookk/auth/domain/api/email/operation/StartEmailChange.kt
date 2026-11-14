package com.bookk.auth.domain.api.email.operation

import com.bookk.auth.domain.api.error.AuthErrorCodes
import com.bookk.core.domain.entity.BusinessError
import com.bookk.core.domain.entity.Language
import io.ktor.http.HttpStatusCode
import library.email.EmailVerificationStarted
import kotlin.uuid.Uuid

interface StartEmailChange {

    suspend operator fun invoke(userId: Uuid, email: String, language: Language): Result<EmailVerificationStarted>

    sealed interface Error {
        class EmailAlreadyExist :
            BusinessError(
                statusCode = HttpStatusCode.UnprocessableEntity.value,
                code = AuthErrorCodes.EMAIL_EXIST,
                message = "This email already exists",
            ),
            Error

        class InvalidEmailFormat :
            BusinessError(
                statusCode = HttpStatusCode.UnprocessableEntity.value,
                code = AuthErrorCodes.INVALID_EMAIL_FORMAT,
                message = "Invalid email format",
            ),
            Error
    }
}
