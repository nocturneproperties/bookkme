package com.bookk.auth.domain.api.telegram.operation

import com.bookk.auth.domain.api.error.AuthErrorCodes
import com.bookk.auth.domain.api.telegram.entity.TelegramSignInRequest
import com.bookk.auth.domain.api.token.entity.AuthTokens
import com.bookk.core.domain.entity.BusinessError
import com.bookk.core.domain.entity.Language
import io.ktor.http.HttpStatusCode

interface SignInWithTelegram {
    suspend operator fun invoke(request: TelegramSignInRequest, language: Language): Result<AuthTokens>

    sealed interface Error {
        class TelegramDataInvalid :
            BusinessError(
                statusCode = HttpStatusCode.UnprocessableEntity.value,
                code = AuthErrorCodes.TELEGRAM_DATA_INVALID,
                message = "Telegram data is not signed by Telegram for this bot",
            ),
            Error

        class TelegramDataExpired :
            BusinessError(
                statusCode = HttpStatusCode.UnprocessableEntity.value,
                code = AuthErrorCodes.TELEGRAM_DATA_EXPIRED,
                message = "Telegram data is too old, reopen the Mini App",
            ),
            Error

        class AccountCreationFailed :
            BusinessError(
                statusCode = HttpStatusCode.UnprocessableEntity.value,
                code = AuthErrorCodes.ACCOUNT_CREATION_FAILED,
                message = "Error during account creation, try again later",
            ),
            Error

        class TelegramSignInUnavailable :
            BusinessError(
                statusCode = HttpStatusCode.ServiceUnavailable.value,
                code = AuthErrorCodes.TELEGRAM_SIGN_IN_UNAVAILABLE,
                message = "Telegram sign in is not set up on this server",
            ),
            Error
    }
}
