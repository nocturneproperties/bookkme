package com.bookk.notifications.domain.impl.notification.renderer.account

import com.bookk.notifications.domain.impl.notification.EmailNotification
import com.bookk.notifications.domain.impl.notification.NotificationParameters
import com.bookk.notifications.domain.impl.notification.NotificationType
import com.bookk.notifications.domain.impl.notification.PushNotification
import com.bookk.notifications.domain.impl.notification.TextNotification
import com.bookk.notifications.domain.impl.notification.renderer.formatLocalized
import com.bookk.notifications.domain.impl.notification.renderer.message
import com.bookk.server.business.client.api.event.BusinessEvent
import com.bookk.server.user.client.api.event.UserEvent
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

internal val UserEvent.DataExportReady.notification: NotificationParameters
    get() = exportReadyNotification("account.data_export_ready", expiresAt)

internal val BusinessEvent.ExportReady.notification: NotificationParameters
    get() = exportReadyNotification("account.business_data_export_ready", expiresAt)

private fun exportReadyNotification(keyPrefix: String, expiresAt: Instant) = NotificationParameters(
    type = NotificationType.ACCOUNT,
    push = { language ->
        PushNotification(
            title = message(language, "$keyPrefix.push.title"),
            body = message(language, "$keyPrefix.push.body", expiresAt.formatLocalized(TimeZone.UTC, language)),
        )
    },
    email = { language ->
        EmailNotification(
            subject = message(language, "$keyPrefix.email.subject"),
            body = message(language, "$keyPrefix.email.body", expiresAt.formatLocalized(TimeZone.UTC, language)),
        )
    },
    text = { language ->
        TextNotification(message(language, "$keyPrefix.text.body", expiresAt.formatLocalized(TimeZone.UTC, language)))
    },
)
