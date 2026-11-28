package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.api.entity.PendingRequestRef
import com.bookk.core.data.eventstreaming.StandardEventProducer
import com.bookk.core.data.eventstreaming.send
import com.bookk.server.appointments.client.api.event.AppointmentEvent

internal suspend fun StandardEventProducer.sendRequestsResolved(requests: List<PendingRequestRef>) {
    requests.forEach { request -> send(AppointmentEvent.RequestResolved(request.id, request.employeeUserId)) }
}
