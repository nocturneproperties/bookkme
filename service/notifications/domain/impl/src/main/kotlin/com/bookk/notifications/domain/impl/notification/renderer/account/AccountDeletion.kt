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

private val AuthEvent.AccountDeletionScheduled.keyPrefix: String
    get() = if (reminder) "account.deletion_reminder" else "account.deletion_scheduled"

internal val AuthEvent.AccountDeletionScheduled.notification: NotificationParameters
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

internal val AuthEvent.AccountDeletionCancelled.notification: NotificationParameters
    get() = NotificationParameters(
        type = NotificationType.ACCOUNT,
        push = { language ->
            PushNotification(
                title = message(language, "account.deletion_cancelled.push.title"),
                body = message(language, "account.deletion_cancelled.push.body"),
            )
        },
        email = { language ->
            EmailNotification(
                subject = message(language, "account.deletion_cancelled.email.subject"),
                body = message(language, "account.deletion_cancelled.email.body"),
            )
        },
        text = { language ->
            TextNotification(message(language, "account.deletion_cancelled.text.body"))
        },
    )
