package com.bookk.auth.domain.api.telegram.operation

import com.bookk.auth.domain.api.actionconfirmation.entity.ActionConfirmationToken
import com.bookk.auth.domain.api.error.AuthErrorCodes
import com.bookk.auth.domain.api.telegram.entity.TelegramActionConfirmationRequest
import com.bookk.core.domain.entity.BusinessError
import io.ktor.http.HttpStatusCode
import kotlin.uuid.Uuid

interface ConfirmActionWithTelegram {
    suspend operator fun invoke(
        userId: Uuid,
        request: TelegramActionConfirmationRequest,
    ): Result<ActionConfirmationToken>

    sealed interface Error {
        class TelegramConfirmationExpired :
            BusinessError(
                statusCode = HttpStatusCode.UnprocessableEntity.value,
                code = AuthErrorCodes.TELEGRAM_CONFIRMATION_EXPIRED,
                message = "Telegram confirmation expired or was already used, ask for a new one",
            ),
            Error

        class TelegramAccountMismatch :
            BusinessError(
                statusCode = HttpStatusCode.UnprocessableEntity.value,
                code = AuthErrorCodes.TELEGRAM_ACCOUNT_MISMATCH,
                message = "The confirmation was opened from a Telegram account not linked to this account",
            ),
            Error
    }
}
