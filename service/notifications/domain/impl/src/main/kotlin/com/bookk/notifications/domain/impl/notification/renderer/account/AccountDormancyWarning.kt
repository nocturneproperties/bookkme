package com.bookk.notifications.domain.impl.notification.renderer.account

import com.bookk.notifications.domain.impl.notification.EmailNotification
import com.bookk.notifications.domain.impl.notification.NotificationParameters
import com.bookk.notifications.domain.impl.notification.NotificationType
import com.bookk.notifications.domain.impl.notification.PushNotification
import com.bookk.notifications.domain.impl.notification.TextNotification
import com.bookk.notifications.domain.impl.notification.renderer.formatLocalized
import com.bookk.notifications.domain.impl.notification.renderer.message
import com.bookk.server.auth.client.AuthEvent
import kotlinx.datetime.TimeZone

private val AuthEvent.AccountDormancyWarning.keyPrefix: String
    get() = if (final) "account.dormancy_final_warning" else "account.dormancy_warning"

internal val AuthEvent.AccountDormancyWarning.notification: NotificationParameters
    get() = NotificationParameters(
        type = NotificationType.ACCOUNT,
        push = { language ->
            PushNotification(
                title = message(language, "$keyPrefix.push.title"),
                body = message(language, "$keyPrefix.push.body", deletesAt.formatLocalized(TimeZone.UTC, language)),
            )
        },
        email = { language ->
            val deletionDate = deletesAt.formatLocalized(TimeZone.UTC, language)
            EmailNotification(
                subject = message(language, "$keyPrefix.email.subject", deletionDate),
                body = message(language, "$keyPrefix.email.body", deletionDate),
            )
        },
        text = { language ->
            TextNotification(
                message(language, "$keyPrefix.text.body", deletesAt.formatLocalized(TimeZone.UTC, language)),
            )
        },
    )
