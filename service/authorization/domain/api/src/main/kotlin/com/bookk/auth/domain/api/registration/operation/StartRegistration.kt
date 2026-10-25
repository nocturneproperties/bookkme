package com.bookk.auth.domain.api.registration.operation

import com.bookk.auth.domain.api.error.AuthErrorCodes
import com.bookk.auth.domain.api.registration.entity.CreateAccountRequest
import com.bookk.auth.domain.api.registration.entity.RegistrationChallengeResponse
import com.bookk.core.domain.entity.BusinessError
import io.ktor.http.HttpStatusCode

interface StartRegistration {

    suspend operator fun invoke(request: CreateAccountRequest): Result<RegistrationChallengeResponse>

    sealed interface Error {
        class EmailAlreadyExist : BusinessError(
            statusCode = HttpStatusCode.UnprocessableEntity.value,
            code = AuthErrorCodes.EMAIL_EXIST,
            message = "This email already exists"
        ), Error

        class InvalidEmailFormat : BusinessError(
            statusCode = HttpStatusCode.UnprocessableEntity.value,
            code = AuthErrorCodes.INVALID_EMAIL_FORMAT,
            message = "Invalid email format"
        ), Error
    }
}