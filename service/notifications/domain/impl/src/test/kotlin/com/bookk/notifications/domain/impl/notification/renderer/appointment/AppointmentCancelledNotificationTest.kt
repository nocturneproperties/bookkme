package com.bookk.notifications.domain.impl.notification.renderer.appointment

import com.bookk.core.domain.entity.Language
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.notifications.domain.impl.notification.renderer.formatLocalized
import com.bookk.server.appointments.client.api.event.AppointmentEvent
import com.bookk.server.appointments.client.api.event.CancellationReasonKind
import kotlinx.datetime.TimeZone
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class AppointmentCancelledNotificationTest {

    private fun event(
        reason: String = "Staff unavailable",
        reasonKind: CancellationReasonKind = CancellationReasonKind.STATED,
    ) = AppointmentEvent.Cancelled(
        clientUserId = Uuid.random(),
        employeeUserId = Uuid.random(),
        from = Instant.parse("2026-03-05T14:30:00Z"),
        to = Instant.parse("2026-03-05T15:00:00Z"),
        timeZone = TimeZone.UTC,
        businessName = "Barbershop",
        price = "USD 20.00",
        reason = reason,
        reasonKind = reasonKind,
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

    @Test
    fun `should say no reason was given in the English push when the reason is empty`() = runUnitTest {
        given()
        val event = event(reason = " ")
        val whenText = event.from.formatLocalized(event.timeZone, Language.EN)

        whenn()
        val push = event.notification.push(Language.EN)

        then()
        assertEquals("Your appointment at Barbershop on $whenText was cancelled: no reason given", push.body)
    }

    @Test
    fun `should say no reason was given in the English email when the reason is empty`() = runUnitTest {
        given()
        val event = event(reason = "")
        val whenText = event.from.formatLocalized(event.timeZone, Language.EN)

        whenn()
        val email = event.notification.email(Language.EN)

        then()
        assertEquals(
            "Your appointment at Barbershop on $whenText was cancelled: no reason given.",
            email.body,
        )
    }

    @Test
    fun `should say no reason was given in the Ukrainian text when the reason is empty`() = runUnitTest {
        given()
        val event = event(reason = "")
        val whenText = event.from.formatLocalized(event.timeZone, Language.UK)

        whenn()
        val text = event.notification.text(Language.UK)

        then()
        assertEquals("Ваш запис до Barbershop на $whenText скасовано: причину не вказано.", text.text)
    }

    @Test
    fun `should say no reason was given in the Polish text when the reason is empty`() = runUnitTest {
        given()
        val event = event(reason = "")
        val whenText = event.from.formatLocalized(event.timeZone, Language.PL)

        whenn()
        val text = event.notification.text(Language.PL)

        then()
        assertEquals("Twoja wizyta w Barbershop na $whenText została odwołana: nie podano powodu.", text.text)
    }

    @Test
    fun `should tell the client in English that the business removed them`() = runUnitTest {
        given()
        val event =
            event(reason = "Client was removed by the business", reasonKind = CancellationReasonKind.CLIENT_REMOVED)
        val whenText = event.from.formatLocalized(event.timeZone, Language.EN)

        whenn()
        val push = event.notification.push(Language.EN)

        then()
        assertEquals(
            "Your appointment at Barbershop on $whenText was cancelled: the business removed you from its clients",
            push.body,
        )
    }

    @Test
    fun `should tell the client in Ukrainian that the business removed them`() = runUnitTest {
        given()
        val event =
            event(reason = "Client was removed by the business", reasonKind = CancellationReasonKind.CLIENT_REMOVED)
        val whenText = event.from.formatLocalized(event.timeZone, Language.UK)

        whenn()
        val text = event.notification.text(Language.UK)

        then()
        assertEquals("Ваш запис до Barbershop на $whenText скасовано: бізнес видалив вас зі своїх клієнтів.", text.text)
    }

    @Test
    fun `should tell the client in Polish that the business removed them`() = runUnitTest {
        given()
        val event =
            event(reason = "Client was removed by the business", reasonKind = CancellationReasonKind.CLIENT_REMOVED)
        val whenText = event.from.formatLocalized(event.timeZone, Language.PL)

        whenn()
        val email = event.notification.email(Language.PL)

        then()
        assertEquals(
            "Twoja wizyta w Barbershop na $whenText została odwołana: firma usunęła Cię ze swoich klientów.",
            email.body,
        )
    }

    @Test
    fun `should tell the client in English that the employee deleted their account`() = runUnitTest {
        given()
        val event =
            event(reason = "Employee deleted their account", reasonKind = CancellationReasonKind.EMPLOYEE_DELETED)
        val whenText = event.from.formatLocalized(event.timeZone, Language.EN)

        whenn()
        val text = event.notification.text(Language.EN)

        then()
        assertEquals(
            "Your appointment at Barbershop on $whenText was cancelled: the employee deleted their account.",
            text.text,
        )
    }

    @Test
    fun `should tell the client in Ukrainian that the employee deleted their account`() = runUnitTest {
        given()
        val event =
            event(reason = "Employee deleted their account", reasonKind = CancellationReasonKind.EMPLOYEE_DELETED)
        val whenText = event.from.formatLocalized(event.timeZone, Language.UK)

        whenn()
        val push = event.notification.push(Language.UK)

        then()
        assertEquals("Ваш запис до Barbershop на $whenText скасовано: працівник видалив свій акаунт", push.body)
    }

    @Test
    fun `should tell the client in Polish that the employee deleted their account`() = runUnitTest {
        given()
        val event =
            event(reason = "Employee deleted their account", reasonKind = CancellationReasonKind.EMPLOYEE_DELETED)
        val whenText = event.from.formatLocalized(event.timeZone, Language.PL)

        whenn()
        val text = event.notification.text(Language.PL)

        then()
        assertEquals(
            "Twoja wizyta w Barbershop na $whenText została odwołana: pracownik usunął swoje konto.",
            text.text,
        )
    }

    @Test
    fun `should leave the address out of the Ukrainian email`() = runUnitTest {
        given()
        val event = event()
        val whenText = event.from.formatLocalized(event.timeZone, Language.UK)

        whenn()
        val email = event.notification.email(Language.UK)

        then()
        assertEquals("Ваш запис до Barbershop на $whenText скасовано: Staff unavailable.", email.body)
    }
}
