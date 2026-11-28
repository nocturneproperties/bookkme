package com.bookk.notifications.domain.impl.notification.renderer.appointment

import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.server.appointments.client.api.event.AppointmentEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class AppointmentRequestResolvedPushTest {

    @Test
    fun `should name the push type and the resolved request in its data`() = runUnitTest {
        given()
        val requestId = Uuid.parse("0c2b6c1e-6f3a-4d0e-9a52-7a1f0b6d8e44")
        val event = AppointmentEvent.RequestResolved(requestId = requestId, employeeUserId = Uuid.random())

        whenn()
        val push = event.push

        then()
        assertEquals(
            mapOf(
                "type" to "appointment_request_resolved",
                "appointmentRequestId" to "0c2b6c1e-6f3a-4d0e-9a52-7a1f0b6d8e44",
            ),
            push.data,
        )
    }
}
