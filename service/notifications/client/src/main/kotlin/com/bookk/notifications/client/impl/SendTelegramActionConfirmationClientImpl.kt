package com.bookk.notifications.client.impl

import com.bookk.core.client.bodyOrThrow
import com.bookk.core.domain.entity.runSuspendCatching
import com.bookk.notifications.client.api.telegram.SendTelegramActionConfirmation
import com.bookk.notifications.client.api.telegram.TelegramActionConfirmationDelivery
import com.bookk.notifications.client.api.telegram.TelegramActionConfirmationMessage
import io.ktor.client.HttpClient
import io.ktor.client.plugins.resources.post
import io.ktor.client.plugins.retry
import io.ktor.client.request.setBody
import kotlin.uuid.Uuid

internal class SendTelegramActionConfirmationClientImpl(
    private val httpClient: HttpClient,
) : SendTelegramActionConfirmation {

    override suspend fun invoke(
        userId: Uuid,
        message: TelegramActionConfirmationMessage,
    ): Result<TelegramActionConfirmationDelivery> = runSuspendCatching {
        httpClient.post(NotificationsRouting.Api.Internal.Telegram.ActionConfirmation(userId = userId)) {
            retry { noRetry() }
            setBody(message)
        }.bodyOrThrow()
    }
}
