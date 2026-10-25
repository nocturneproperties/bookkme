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

internal class AppointmentRequestCreatedNotificationTest {

    private fun event() = AppointmentEvent.RequestCreated(
        clientUserId = Uuid.random(),
        clientName = "Alice",
        employeeUserId = Uuid.random(),
        employeeName = "Bob",
        from = Instant.parse("2026-03-05T14:30:00Z"),
        to = Instant.parse("2026-03-05T15:00:00Z"),
        timeZone = TimeZone.UTC,
        businessName = "Barbershop",
        address = "1 Main St",
        price = "USD 20.00"
    )

    @Test
    fun `should render English push content`() = runUnitTest {
        given()
        val event = event()
        val whenText = event.from.formatLocalized(event.timeZone, Language.EN)

        whenn()
        val push = event.notification.push(Language.EN)

        then()
        assertEquals("New appointment request", push.title)
        assertEquals("Alice wants to book Barbershop on $whenText", push.body)
    }

    @Test
    fun `should render Ukrainian push content`() = runUnitTest {
        given()
        val event = event()
        val whenText = event.from.formatLocalized(event.timeZone, Language.UK)

        whenn()
        val push = event.notification.push(Language.UK)

        then()
        assertEquals("Новий запит на запис", push.title)
        assertEquals("Alice хоче записатися до Barbershop на $whenText", push.body)
    }

    @Test
    fun `should render English email content including address and price`() = runUnitTest {
        given()
        val event = event()

        whenn()
        val email = event.notification.email(Language.EN)

        then()
        assertTrue(email.subject.contains("New appointment request"))
        assertTrue(email.subject.contains("Alice"))
        assertTrue(email.body.contains(event.address))
        assertTrue(email.body.contains(event.price))
    }

    @Test
    fun `should render Ukrainian email content including address and price`() = runUnitTest {
        given()
        val event = event()

        whenn()
        val email = event.notification.email(Language.UK)

        then()
        assertTrue(email.subject.contains("Новий запит на запис"))
        assertTrue(email.subject.contains("Alice"))
        assertTrue(email.body.contains(event.address))
        assertTrue(email.body.contains(event.price))
    }

    @Test
    fun `should render English text content`() = runUnitTest {
        given()
        val event = event()
        val whenText = event.from.formatLocalized(event.timeZone, Language.EN)

        whenn()
        val text = event.notification.text(Language.EN)

        then()
        assertEquals("Alice requested an appointment at Barbershop on $whenText.", text.text)
    }

    @Test
    fun `should render Ukrainian text content`() = runUnitTest {
        given()
        val event = event()
        val whenText = event.from.formatLocalized(event.timeZone, Language.UK)

        whenn()
        val text = event.notification.text(Language.UK)

        then()
        assertEquals("Alice запросив(ла) запис до Barbershop на $whenText.", text.text)
    }
}
