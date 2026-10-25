package com.bookk.auth.domain.api.token.operation

import com.bookk.auth.domain.api.error.AuthErrorCodes
import com.bookk.auth.domain.api.token.entity.AuthTokens
import com.bookk.core.domain.entity.BusinessError
import io.ktor.http.HttpStatusCode

interface RefreshToken {

    suspend operator fun invoke(token: String): Result<AuthTokens>

    sealed interface Error {
        class InvalidRefreshToken : BusinessError(HttpStatusCode.UnprocessableEntity.value, AuthErrorCodes.INVALID_CREDENTIALS, "Invalid refresh token")
    }
}