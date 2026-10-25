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

internal class AppointmentRequestRejectedNotificationTest {

    private fun event() = AppointmentEvent.RequestRejected(
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
        declineReason = "Fully booked"
    )

    @Test
    fun `should render English push content including the decline reason`() = runUnitTest {
        given()
        val event = event()
        val whenText = event.from.formatLocalized(event.timeZone, Language.EN)

        whenn()
        val push = event.notification.push(Language.EN)

        then()
        assertEquals("Appointment declined", push.title)
        assertEquals("Your request for Barbershop on $whenText was declined: Fully booked", push.body)
        // Language-specific assertion: ensure this is English, not Ukrainian
        assertTrue(push.title.contains("declined"), "Push title should contain English word 'declined'")
    }

    @Test
    fun `should render Ukrainian push content including the decline reason`() = runUnitTest {
        given()
        val event = event()
        val whenText = event.from.formatLocalized(event.timeZone, Language.UK)

        whenn()
        val push = event.notification.push(Language.UK)

        then()
        assertEquals("Запис відхилено", push.title)
        assertEquals("Ваш запит до Barbershop на $whenText відхилено: Fully booked", push.body)
        // Language-specific assertion: ensure this is Ukrainian, not English
        assertTrue(push.title.contains("відхилено"), "Push title should contain Ukrainian word 'відхилено'")
    }

    @Test
    fun `should render English email content including the decline reason`() = runUnitTest {
        given()
        val event = event()

        whenn()
        val email = event.notification.email(Language.EN)

        then()
        assertTrue(email.subject.contains("Barbershop"))
        assertTrue(email.body.contains(event.declineReason))
        // Language-specific assertion: ensure this is English, not Ukrainian
        assertTrue(email.subject.contains("declined"), "Email subject should contain English word 'declined'")
    }

    @Test
    fun `should render Ukrainian email content including the decline reason`() = runUnitTest {
        given()
        val event = event()

        whenn()
        val email = event.notification.email(Language.UK)

        then()
        assertTrue(email.subject.contains("Barbershop"))
        assertTrue(email.body.contains(event.declineReason))
        // Language-specific assertion: ensure this is Ukrainian, not English
        assertTrue(email.subject.contains("відхилено"), "Email subject should contain Ukrainian word 'відхилено'")
    }

    @Test
    fun `should render English text content including the decline reason`() = runUnitTest {
        given()
        val event = event()
        val whenText = event.from.formatLocalized(event.timeZone, Language.EN)

        whenn()
        val text = event.notification.text(Language.EN)

        then()
        assertEquals("Your request for Barbershop on $whenText was declined: Fully booked.", text.text)
        // Language-specific assertion: ensure this is English, not Ukrainian
        assertTrue(text.text.contains("declined"), "Text should contain English word 'declined'")
    }

    @Test
    fun `should render Ukrainian text content including the decline reason`() = runUnitTest {
        given()
        val event = event()
        val whenText = event.from.formatLocalized(event.timeZone, Language.UK)

        whenn()
        val text = event.notification.text(Language.UK)

        then()
        assertEquals("Ваш запит до Barbershop на $whenText відхилено: Fully booked.", text.text)
        // Language-specific assertion: ensure this is Ukrainian, not English
        assertTrue(text.text.contains("відхилено"), "Text should contain Ukrainian word 'відхилено'")
    }
}
