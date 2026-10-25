package com.bookk.notifications.domain.impl.notification.renderer.appointment

import com.bookk.notifications.domain.impl.notification.EmailNotification
import com.bookk.notifications.domain.impl.notification.NotificationParameters
import com.bookk.notifications.domain.impl.notification.NotificationType
import com.bookk.notifications.domain.impl.notification.PushNotification
import com.bookk.notifications.domain.impl.notification.TextNotification
import com.bookk.notifications.domain.impl.notification.renderer.formatLocalized
import com.bookk.notifications.domain.impl.notification.renderer.message
import com.bookk.server.appointments.client.api.event.AppointmentEvent

private const val KEY_PREFIX = "appointment.cancelled"

internal val AppointmentEvent.Cancelled.notification: NotificationParameters
    get() = NotificationParameters(
        type = NotificationType.APPOINTMENT,
        push = { language ->
            val whenText = from.formatLocalized(timeZone, language)
            PushNotification(
                title = message(language, "$KEY_PREFIX.push.title"),
                body = message(language, "$KEY_PREFIX.push.body", businessName, whenText, reason)
            )
        },
        email = { language ->
            val whenText = from.formatLocalized(timeZone, language)
            EmailNotification(
                subject = message(language, "$KEY_PREFIX.email.subject", businessName),
                body = message(language, "$KEY_PREFIX.email.body", businessName, whenText, reason, address)
            )
        },
        text = { language ->
            val whenText = from.formatLocalized(timeZone, language)
            TextNotification(message(language, "$KEY_PREFIX.text.body", businessName, whenText, reason))
        }
    )
