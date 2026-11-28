package com.bookk.notifications.domain.impl.notification.renderer.appointment

import com.bookk.notifications.domain.impl.notification.DataPush
import com.bookk.notifications.domain.impl.notification.NotificationType
import com.bookk.server.appointments.client.api.event.AppointmentEvent

private const val PUSH_TYPE_DATA_KEY = "type"
private const val REQUEST_RESOLVED_PUSH_TYPE = "appointment_request_resolved"

internal val AppointmentEvent.RequestResolved.push: DataPush
    get() = DataPush(
        type = NotificationType.APPOINTMENT,
        data = mapOf(
            PUSH_TYPE_DATA_KEY to REQUEST_RESOLVED_PUSH_TYPE,
            APPOINTMENT_REQUEST_ID_DATA_KEY to requestId.toString(),
        ),
    )
