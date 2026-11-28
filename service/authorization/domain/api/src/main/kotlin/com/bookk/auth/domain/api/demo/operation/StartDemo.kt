package com.bookk.auth.domain.api.demo.operation

import com.bookk.auth.domain.api.demo.entity.DemoStartRequest
import com.bookk.auth.domain.api.error.AuthErrorCodes
import com.bookk.auth.domain.api.token.entity.AuthTokens
import com.bookk.core.domain.entity.BusinessError
import com.bookk.core.domain.entity.Language
import io.ktor.http.HttpStatusCode

interface StartDemo {
    suspend operator fun invoke(request: DemoStartRequest, language: Language): Result<AuthTokens>

    sealed interface Error {
        class DemoUnavailable :
            BusinessError(
                statusCode = HttpStatusCode.TooManyRequests.value,
                code = AuthErrorCodes.DEMO_UNAVAILABLE,
                message = "No demo account can be started right now, try again later",
            ),
            Error

        class AccountCreationFailed :
            BusinessError(
                statusCode = HttpStatusCode.UnprocessableEntity.value,
                code = AuthErrorCodes.ACCOUNT_CREATION_FAILED,
                message = "Error during account creation, try again later",
            ),
            Error
    }
}
