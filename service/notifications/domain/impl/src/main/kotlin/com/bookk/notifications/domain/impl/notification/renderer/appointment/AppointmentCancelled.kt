package com.bookk.notifications.domain.impl.notification.renderer.appointment

import com.bookk.core.domain.entity.Language
import com.bookk.notifications.domain.impl.notification.EmailNotification
import com.bookk.notifications.domain.impl.notification.NotificationParameters
import com.bookk.notifications.domain.impl.notification.NotificationType
import com.bookk.notifications.domain.impl.notification.PushNotification
import com.bookk.notifications.domain.impl.notification.TextNotification
import com.bookk.notifications.domain.impl.notification.renderer.formatLocalized
import com.bookk.notifications.domain.impl.notification.renderer.message
import com.bookk.server.appointments.client.api.event.AppointmentEvent
import com.bookk.server.appointments.client.api.event.CancellationReasonKind

private const val KEY_PREFIX = "appointment.cancelled"

internal val AppointmentEvent.Cancelled.notification: NotificationParameters
    get() = NotificationParameters(
        type = NotificationType.APPOINTMENT,
        push = { language ->
            val whenText = from.formatLocalized(timeZone, language)
            PushNotification(
                title = message(language, "$KEY_PREFIX.push.title"),
                body = message(
                    language,
                    "$KEY_PREFIX.push.body",
                    businessName,
                    whenText,
                    reasonText(language),
                ),
            )
        },
        email = { language ->
            val whenText = from.formatLocalized(timeZone, language)
            EmailNotification(
                subject = message(language, "$KEY_PREFIX.email.subject", businessName),
                body = message(
                    language,
                    "$KEY_PREFIX.email.body",
                    businessName,
                    whenText,
                    reasonText(language),
                ),
            )
        },
        text = { language ->
            val whenText = from.formatLocalized(timeZone, language)
            TextNotification(
                message(language, "$KEY_PREFIX.text.body", businessName, whenText, reasonText(language)),
            )
        },
    )

private fun AppointmentEvent.Cancelled.reasonText(language: Language): String {
    return when (reasonKind) {
        CancellationReasonKind.STATED -> reason.orMissingReason(language)
        CancellationReasonKind.CLIENT_REMOVED -> message(language, "appointment.reason.client_removed")
        CancellationReasonKind.EMPLOYEE_DELETED -> message(language, "appointment.reason.employee_deleted")
    }
}
