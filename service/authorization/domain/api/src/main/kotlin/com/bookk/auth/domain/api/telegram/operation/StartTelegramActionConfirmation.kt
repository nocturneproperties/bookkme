package com.bookk.auth.domain.api.telegram.operation

import com.bookk.auth.domain.api.error.AuthErrorCodes
import com.bookk.auth.domain.api.telegram.entity.TelegramActionConfirmationStart
import com.bookk.core.domain.entity.BusinessError
import com.bookk.core.domain.entity.Language
import io.ktor.http.HttpStatusCode
import kotlin.uuid.Uuid

interface StartTelegramActionConfirmation {
    suspend operator fun invoke(
        userId: Uuid,
        request: TelegramActionConfirmationStart,
        language: Language,
    ): Result<Unit>

    sealed interface Error {
        class ActionNotConfirmableThroughTelegram :
            BusinessError(
                statusCode = HttpStatusCode.UnprocessableEntity.value,
                code = AuthErrorCodes.ACTION_NOT_CONFIRMABLE_THROUGH_TELEGRAM,
                message = "Only deleting your own account or exporting your own data can be confirmed through Telegram",
            ),
            Error

        class TelegramIdentityMissing :
            BusinessError(
                statusCode = HttpStatusCode.UnprocessableEntity.value,
                code = AuthErrorCodes.TELEGRAM_IDENTITY_MISSING,
                message = "This account is not linked to Telegram",
            ),
            Error

        class TelegramConfirmationTooFrequent :
            BusinessError(
                statusCode = HttpStatusCode.TooManyRequests.value,
                code = AuthErrorCodes.TELEGRAM_CONFIRMATION_TOO_FREQUENT,
                message = "Wait a minute before asking for another Telegram confirmation",
            ),
            Error

        class TelegramChatUnreachable :
            BusinessError(
                statusCode = HttpStatusCode.UnprocessableEntity.value,
                code = AuthErrorCodes.TELEGRAM_CHAT_UNREACHABLE,
                message = "The bot can't write to you on Telegram, allow it to send messages and try again",
            ),
            Error
    }
}
