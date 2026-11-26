package com.bookk.notifications.client.api.telegram

import kotlin.uuid.Uuid

interface SendTelegramActionConfirmation {
    suspend operator fun invoke(
        userId: Uuid,
        message: TelegramActionConfirmationMessage,
    ): Result<TelegramActionConfirmationDelivery>
}
