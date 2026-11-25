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
import org.junit.jupiter.api.Test
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class AppointmentRequestCreatedNotificationTest {

    private fun event(serviceNames: List<String> = listOf("Haircut", "Beard trim", "Beard trim")) =
        AppointmentEvent.RequestCreated(
            clientUserId = Uuid.random(),
            employeeUserId = Uuid.random(),
            from = Instant.parse("2026-03-05T14:30:00Z"),
            to = Instant.parse("2026-03-05T15:00:00Z"),
            timeZone = TimeZone.UTC,
            price = "USD 20.00",
            serviceNames = serviceNames,
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
        assertEquals("You have a new appointment request on $whenText", push.body)
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
        assertEquals("У вас новий запит на $whenText", push.body)
    }

    @Test
    fun `should render English email content with the services and the price`() = runUnitTest {
        given()
        val event = event()
        val whenText = event.from.formatLocalized(event.timeZone, Language.EN)

        whenn()
        val email = event.notification.email(Language.EN)

        then()
        assertEquals("New appointment request", email.subject)
        assertEquals(
            "You have a new appointment request on $whenText.\nServices: Haircut, Beard trim ×2\nPrice: USD 20.00.",
            email.body,
        )
    }

    @Test
    fun `should render Ukrainian email content with the services and the price`() = runUnitTest {
        given()
        val event = event()
        val whenText = event.from.formatLocalized(event.timeZone, Language.UK)

        whenn()
        val email = event.notification.email(Language.UK)

        then()
        assertEquals("Новий запит на запис", email.subject)
        assertEquals(
            "У вас новий запит на $whenText.\nПослуги: Haircut, Beard trim ×2\nВартість: USD 20.00.",
            email.body,
        )
    }

    @Test
    fun `should render Polish email content with the services and the price`() = runUnitTest {
        given()
        val event = event()
        val whenText = event.from.formatLocalized(event.timeZone, Language.PL)

        whenn()
        val email = event.notification.email(Language.PL)

        then()
        assertEquals("Nowa prośba o wizytę", email.subject)
        assertEquals(
            "Masz nową prośbę o wizytę na $whenText.\nUsługi: Haircut, Beard trim ×2\nCena: USD 20.00.",
            email.body,
        )
    }

    @Test
    fun `should list a service booked once without a count`() = runUnitTest {
        given()
        val event = event(serviceNames = listOf("Haircut"))
        val whenText = event.from.formatLocalized(event.timeZone, Language.EN)

        whenn()
        val email = event.notification.email(Language.EN)

        then()
        assertEquals(
            "You have a new appointment request on $whenText.\nServices: Haircut\nPrice: USD 20.00.",
            email.body,
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
        assertEquals("You have a new appointment request on $whenText.", text.text)
    }

    @Test
    fun `should render Ukrainian text content`() = runUnitTest {
        given()
        val event = event()
        val whenText = event.from.formatLocalized(event.timeZone, Language.UK)

        whenn()
        val text = event.notification.text(Language.UK)

        then()
        assertEquals("У вас новий запит на $whenText.", text.text)
    }
}
