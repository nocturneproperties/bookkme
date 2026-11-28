package com.bookk.notifications.domain.impl.notification.renderer.appointment

import com.bookk.notifications.domain.impl.notification.EmailNotification
import com.bookk.notifications.domain.impl.notification.NotificationParameters
import com.bookk.notifications.domain.impl.notification.NotificationType
import com.bookk.notifications.domain.impl.notification.PushNotification
import com.bookk.notifications.domain.impl.notification.PushSubject
import com.bookk.notifications.domain.impl.notification.TextNotification
import com.bookk.notifications.domain.impl.notification.renderer.formatLocalized
import com.bookk.notifications.domain.impl.notification.renderer.message
import com.bookk.server.appointments.client.api.event.AppointmentEvent
import kotlin.uuid.Uuid

private const val KEY_PREFIX = "appointment.request_created"
private const val PUSH_SUBJECT_PREFIX = "appointment_request:"

internal val AppointmentEvent.RequestCreated.notification: NotificationParameters
    get() = NotificationParameters(
        type = NotificationType.APPOINTMENT,
        push = { language ->
            val whenText = from.formatLocalized(timeZone, language)
            PushNotification(
                title = message(language, "$KEY_PREFIX.push.title"),
                body = message(language, "$KEY_PREFIX.push.body", whenText),
                subject = requestId?.let(::requestPushSubject),
            )
        },
        email = { language ->
            val whenText = from.formatLocalized(timeZone, language)
            EmailNotification(
                subject = message(language, "$KEY_PREFIX.email.subject"),
                body = message(language, "$KEY_PREFIX.email.body", whenText, serviceNames.servicesText(), price),
            )
        },
        text = { language ->
            val whenText = from.formatLocalized(timeZone, language)
            TextNotification(message(language, "$KEY_PREFIX.text.body", whenText))
        },
    )

private fun List<String>.servicesText(): String {
    return groupingBy { it }.eachCount().entries.joinToString(", ") { (name, count) ->
        if (count == 1) name else "$name ×$count"
    }
}

private fun requestPushSubject(requestId: Uuid): PushSubject {
    return PushSubject(
        key = "$PUSH_SUBJECT_PREFIX$requestId",
        data = mapOf(APPOINTMENT_REQUEST_ID_DATA_KEY to requestId.toString()),
    )
}
