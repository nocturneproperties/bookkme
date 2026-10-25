package com.bookk.notifications.domain.impl.notification.renderer.employee

import com.bookk.core.domain.entity.Language
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.notifications.domain.impl.notification.NotificationType
import com.bookk.server.business.client.api.event.BusinessEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class EmployeeInvitationRedeemedNotificationTest {

    private fun event() = BusinessEvent.EmployeeInvitationRedeemed(
        inviterUserId = Uuid.random(),
        employeeUserId = Uuid.random(),
        employeeName = "Alice",
        businessId = Uuid.random(),
        businessName = "Barbershop"
    )

    @Test
    fun `should be rendered as an employee notification type`() = runUnitTest {
        given()
        val event = event()

        whenn()
        val type = event.notification.type

        then()
        assertEquals(NotificationType.EMPLOYEE, type)
    }

    @Test
    fun `should render English push content`() = runUnitTest {
        given()
        val event = event()

        whenn()
        val push = event.notification.push(Language.EN)

        then()
        assertEquals("Invitation accepted", push.title)
        assertEquals("Alice accepted your invitation to join Barbershop", push.body)
    }

    @Test
    fun `should render Ukrainian push content`() = runUnitTest {
        given()
        val event = event()

        whenn()
        val push = event.notification.push(Language.UK)

        then()
        assertEquals("Запрошення прийнято", push.title)
        assertEquals("Alice прийняв(ла) ваше запрошення приєднатися до Barbershop", push.body)
    }

    @Test
    fun `should render English email content`() = runUnitTest {
        given()
        val event = event()

        whenn()
        val email = event.notification.email(Language.EN)

        then()
        assertEquals("Alice accepted your invitation", email.subject)
        assertTrue(email.body.contains("Alice"))
        assertTrue(email.body.contains("Barbershop"))
    }

    @Test
    fun `should render Ukrainian email content`() = runUnitTest {
        given()
        val event = event()

        whenn()
        val email = event.notification.email(Language.UK)

        then()
        assertEquals("Alice прийняв(ла) ваше запрошення", email.subject)
        assertTrue(email.body.contains("Alice"))
        assertTrue(email.body.contains("Barbershop"))
    }

    @Test
    fun `should render English text content`() = runUnitTest {
        given()
        val event = event()

        whenn()
        val text = event.notification.text(Language.EN)

        then()
        assertEquals("Alice accepted your invitation to join Barbershop.", text.text)
    }

    @Test
    fun `should render Ukrainian text content`() = runUnitTest {
        given()
        val event = event()

        whenn()
        val text = event.notification.text(Language.UK)

        then()
        assertEquals("Alice прийняв(ла) ваше запрошення приєднатися до Barbershop.", text.text)
    }
}
