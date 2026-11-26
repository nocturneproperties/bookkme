package com.bookk.auth.domain.api.telegram.operation

import com.bookk.auth.domain.api.error.AuthErrorCodes
import com.bookk.core.domain.entity.BusinessError
import io.ktor.http.HttpStatusCode

class TelegramConfirmationUnavailable :
    BusinessError(
        statusCode = HttpStatusCode.ServiceUnavailable.value,
        code = AuthErrorCodes.TELEGRAM_CONFIRMATION_UNAVAILABLE,
        message = "Telegram confirmation is not available right now, try again later",
    ),
    StartTelegramActionConfirmation.Error,
    ConfirmActionWithTelegram.Error
