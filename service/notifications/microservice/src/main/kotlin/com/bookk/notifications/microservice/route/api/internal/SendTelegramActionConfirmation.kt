package com.bookk.notifications.microservice.route.api.internal

import com.bookk.core.service.di.injectScoped
import com.bookk.core.service.enity.respondWith
import com.bookk.notifications.client.api.telegram.SendTelegramActionConfirmation
import com.bookk.notifications.client.api.telegram.TelegramActionConfirmationMessage
import com.bookk.notifications.domain.impl.di.NotificationsScope
import com.bookk.notifications.microservice.route.NotificationsRouting.Api
import io.ktor.server.request.receive
import io.ktor.server.resources.post
import io.ktor.server.routing.Routing
import io.ktor.server.routing.application

fun Routing.sendTelegramActionConfirmation() {
    /**
     * Send a Telegram action confirmation link
     *
     * Summary: Send a Telegram action confirmation link
     * Description: The bot writes to the user's Telegram chat, in the requested language, which action they asked to confirm, with a button that opens the Mini App with the given start parameter. Sent even with the Telegram notification channel off. Used by auth to confirm an account deletion or personal data export for an account without a passkey. The outcome says whether the message was sent, Telegram refused it because the bot can't write to the user, notifications has no Telegram chat for the user, or the bot token or username isn't set
     * Tag: internal
     * Body: application/x-protobuf [com.bookk.notifications.client.api.telegram.TelegramActionConfirmationMessage] Action, start parameter and language
     * Response: 200 application/x-protobuf [com.bookk.notifications.client.api.telegram.TelegramActionConfirmationDelivery] Delivery outcome
     * See: docs/operations/notifications/send-telegram-action-confirmation.md
     */
    post<Api.Internal.Telegram.ActionConfirmation> { route ->
        val message = call.receive<TelegramActionConfirmationMessage>()
        val sendTelegramActionConfirmation by application.injectScoped<SendTelegramActionConfirmation>(
            NotificationsScope,
        )
        call.respondWith(sendTelegramActionConfirmation(route.userId, message))
    }
}
