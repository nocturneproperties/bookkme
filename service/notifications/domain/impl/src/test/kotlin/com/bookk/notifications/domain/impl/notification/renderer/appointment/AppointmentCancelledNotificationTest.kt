package com.bookk.notifications.domain.impl.notification.renderer.appointment

import com.bookk.core.domain.entity.Language
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.notifications.domain.impl.notification.renderer.formatLocalized
import com.bookk.server.appointments.client.api.event.AppointmentEvent
import kotlinx.datetime.TimeZone
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class AppointmentCancelledNotificationTest {

    private fun event() = AppointmentEvent.Cancelled(
        clientUserId = Uuid.random(),
        clientName = "Alice",
        employeeUserId = Uuid.random(),
        employeeName = "Bob",
        from = Instant.parse("2026-03-05T14:30:00Z"),
        to = Instant.parse("2026-03-05T15:00:00Z"),
        timeZone = TimeZone.UTC,
        address = "1 Main St",
        businessName = "Barbershop",
        price = "USD 20.00",
        reason = "Staff unavailable"
    )

    @Test
    fun `should render English push content including the cancellation reason`() = runUnitTest {
        given()
        val event = event()
        val whenText = event.from.formatLocalized(event.timeZone, Language.EN)

        whenn()
        val push = event.notification.push(Language.EN)

        then()
        assertEquals("Appointment cancelled", push.title)
        assertEquals("Your appointment at Barbershop on $whenText was cancelled: Staff unavailable", push.body)
    }

    @Test
    fun `should render Ukrainian push content including the cancellation reason`() = runUnitTest {
        given()
        val event = event()
        val whenText = event.from.formatLocalized(event.timeZone, Language.UK)

        whenn()
        val push = event.notification.push(Language.UK)

        then()
        assertEquals("Запис скасовано", push.title)
        assertEquals("Ваш запис до Barbershop на $whenText скасовано: Staff unavailable", push.body)
    }

    @Test
    fun `should render English email content including the cancellation reason`() = runUnitTest {
        given()
        val event = event()

        whenn()
        val email = event.notification.email(Language.EN)

        then()
        assertTrue(email.subject.contains("Barbershop"))
        assertTrue(email.body.contains(event.reason))
        // Language-specific assertion: ensure this is English, not Ukrainian
        assertTrue(email.subject.contains("cancelled"), "Email subject should contain English word 'cancelled'")
    }

    @Test
    fun `should render Ukrainian email content including the cancellation reason`() = runUnitTest {
        given()
        val event = event()

        whenn()
        val email = event.notification.email(Language.UK)

        then()
        assertTrue(email.subject.contains("Barbershop"))
        assertTrue(email.body.contains(event.reason))
        // Language-specific assertion: ensure this is Ukrainian, not English
        assertTrue(email.subject.contains("скасовано"), "Email subject should contain Ukrainian word 'скасовано'")
    }

    @Test
    fun `should render English text content including the cancellation reason`() = runUnitTest {
        given()
        val event = event()
        val whenText = event.from.formatLocalized(event.timeZone, Language.EN)

        whenn()
        val text = event.notification.text(Language.EN)

        then()
        assertEquals("Your appointment at Barbershop on $whenText was cancelled: Staff unavailable.", text.text)
    }

    @Test
    fun `should render Ukrainian text content including the cancellation reason`() = runUnitTest {
        given()
        val event = event()
        val whenText = event.from.formatLocalized(event.timeZone, Language.UK)

        whenn()
        val text = event.notification.text(Language.UK)

        then()
        assertEquals("Ваш запис до Barbershop на $whenText скасовано: Staff unavailable.", text.text)
    }
}
