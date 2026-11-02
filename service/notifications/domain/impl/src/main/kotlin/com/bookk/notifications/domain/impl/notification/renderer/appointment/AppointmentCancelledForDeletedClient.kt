package com.bookk.notifications.domain.impl.notification.renderer.appointment

import com.bookk.notifications.domain.impl.notification.EmailNotification
import com.bookk.notifications.domain.impl.notification.NotificationParameters
import com.bookk.notifications.domain.impl.notification.NotificationType
import com.bookk.notifications.domain.impl.notification.PushNotification
import com.bookk.notifications.domain.impl.notification.TextNotification
import com.bookk.notifications.domain.impl.notification.renderer.formatLocalized
import com.bookk.notifications.domain.impl.notification.renderer.message
import com.bookk.server.appointments.client.api.event.AppointmentEvent
import com.bookk.server.appointments.client.api.event.ClientDeletionCause


private val ClientDeletionCause.keyPrefix: String
    get() = when (this) {
        ClientDeletionCause.ACCOUNT_DELETED -> "appointment.cancelled_for_deleted_client"
        ClientDeletionCause.REMOVED_BY_BUSINESS -> "appointment.cancelled_for_removed_client"
    }

internal val AppointmentEvent.CancelledForDeletedClient.notification: NotificationParameters
    get() = NotificationParameters(
        type = NotificationType.APPOINTMENT,
        push = { language ->
            val whenText = from.formatLocalized(timeZone, language)
            PushNotification(
                title = message(language, "${cause.keyPrefix}.push.title"),
                body = message(language, "${cause.keyPrefix}.push.body", businessName, whenText)
            )
        },
        email = { language ->
            val whenText = from.formatLocalized(timeZone, language)
            EmailNotification(
                subject = message(language, "${cause.keyPrefix}.email.subject", businessName),
                body = message(language, "${cause.keyPrefix}.email.body", businessName, whenText)
            )
        },
        text = { language ->
            val whenText = from.formatLocalized(timeZone, language)
            TextNotification(message(language, "${cause.keyPrefix}.text.body", businessName, whenText))
        }
    )
