package com.bookk.auth.domain.api.token.operation

import com.bookk.auth.domain.api.error.AuthErrorCodes.INVALID_CREDENTIALS
import com.bookk.auth.domain.api.token.entity.AuthTokens
import com.bookk.core.domain.entity.BusinessError
import io.ktor.http.HttpStatusCode
import kotlin.uuid.Uuid

interface GenerateAuthToken {

    suspend operator fun invoke(source: Source): Result<AuthTokens>

    sealed interface Source {
        class RefreshToken(val token: String) : Source
        class InitialAuthentication(val authId: Uuid, val deviceUUID: Uuid) : Source
    }

    sealed interface Error {
        class InvalidCredentials : BusinessError(
            statusCode = HttpStatusCode.Unauthorized.value,
            code = INVALID_CREDENTIALS,
            message = "Invalid credentials"
        ), Error
    }
}