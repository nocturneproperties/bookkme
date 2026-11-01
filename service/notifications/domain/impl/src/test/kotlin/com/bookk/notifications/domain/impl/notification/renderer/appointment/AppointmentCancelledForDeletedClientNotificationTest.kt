package com.bookk.notifications.domain.impl.notification.renderer.appointment

import com.bookk.core.domain.entity.Language
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.notifications.domain.impl.notification.NotificationType
import com.bookk.notifications.domain.impl.notification.renderer.formatLocalized
import com.bookk.server.appointments.client.api.event.AppointmentEvent
import kotlinx.datetime.TimeZone
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class AppointmentCancelledForDeletedClientNotificationTest {

    private fun event() = AppointmentEvent.CancelledForDeletedClient(
        employeeUserId = Uuid.random(),
        from = Instant.parse("2026-03-05T14:30:00Z"),
        to = Instant.parse("2026-03-05T15:00:00Z"),
        timeZone = TimeZone.UTC,
        businessName = "Barbershop"
    )

    @Test
    fun `should be an appointment notification`() = runUnitTest {
        given()
        val event = event()

        whenn()
        val type = event.notification.type

        then()
        assertEquals(NotificationType.APPOINTMENT, type)
    }

    @Test
    fun `should render English push content telling the employee the slot is free`() = runUnitTest {
        given()
        val event = event()
        val whenText = event.from.formatLocalized(event.timeZone, Language.EN)

        whenn()
        val push = event.notification.push(Language.EN)

        then()
        assertEquals("Appointment cancelled", push.title)
        assertEquals("A client deleted their account, so the appointment at Barbershop on $whenText was cancelled", push.body)
    }

    @Test
    fun `should render Ukrainian push content telling the employee the slot is free`() = runUnitTest {
        given()
        val event = event()
        val whenText = event.from.formatLocalized(event.timeZone, Language.UK)

        whenn()
        val push = event.notification.push(Language.UK)

        then()
        assertEquals("Запис скасовано", push.title)
        assertEquals("Клієнт видалив свій акаунт, тож запис до Barbershop на $whenText скасовано", push.body)
    }

    @Test
    fun `should render English email content telling the employee the time is free again`() = runUnitTest {
        given()
        val event = event()
        val whenText = event.from.formatLocalized(event.timeZone, Language.EN)

        whenn()
        val email = event.notification.email(Language.EN)

        then()
        assertEquals("Appointment at Barbershop cancelled", email.subject)
        assertEquals(
            "A client deleted their account, so the appointment at Barbershop on $whenText was cancelled. The time is free again.",
            email.body
        )
    }

    @Test
    fun `should render Ukrainian email content telling the employee the time is free again`() = runUnitTest {
        given()
        val event = event()
        val whenText = event.from.formatLocalized(event.timeZone, Language.UK)

        whenn()
        val email = event.notification.email(Language.UK)

        then()
        assertEquals("Запис до Barbershop скасовано", email.subject)
        assertEquals(
            "Клієнт видалив свій акаунт, тож запис до Barbershop на $whenText скасовано. Цей час знову вільний.",
            email.body
        )
    }

    @Test
    fun `should render English text content`() = runUnitTest {
        given()
        val event = event()
        val whenText = event.from.formatLocalized(event.timeZone, Language.EN)

        whenn()
        val text = event.notification.text(Language.EN)

        then()
        assertEquals("A client deleted their account, so the appointment at Barbershop on $whenText was cancelled.", text.text)
    }

    @Test
    fun `should render Ukrainian text content`() = runUnitTest {
        given()
        val event = event()
        val whenText = event.from.formatLocalized(event.timeZone, Language.UK)

        whenn()
        val text = event.notification.text(Language.UK)

        then()
        assertEquals("Клієнт видалив свій акаунт, тож запис до Barbershop на $whenText скасовано.", text.text)
    }
}
