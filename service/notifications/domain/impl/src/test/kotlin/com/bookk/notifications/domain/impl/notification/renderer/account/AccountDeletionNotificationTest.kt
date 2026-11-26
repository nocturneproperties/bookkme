package com.bookk.notifications.domain.impl.notification.renderer.account

import com.bookk.core.domain.entity.Language
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.notifications.domain.impl.notification.NotificationType
import com.bookk.notifications.domain.impl.notification.renderer.formatLocalized
import com.bookk.server.auth.client.AuthEvent
import kotlinx.datetime.TimeZone
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class AccountDeletionNotificationTest {

    private fun scheduled(reminder: Boolean): AuthEvent.AccountDeletionScheduled {
        return AuthEvent.AccountDeletionScheduled(
            userId = Uuid.random(),
            deletesAt = Instant.parse("2026-12-03T10:00:00Z"),
            reminder = reminder,
        )
    }

    @Test
    fun `should send every deletion notice as an account notification the user cannot switch off`() = runUnitTest {
        given()
        val events = listOf(
            scheduled(reminder = false).notification,
            scheduled(reminder = true).notification,
            AuthEvent.AccountDeletionCancelled(Uuid.random()).notification,
        )

        whenn()
        val types = events.map { it.type }.toSet()

        then()
        assertEquals(setOf(NotificationType.ACCOUNT), types)
    }

    @Test
    fun `should confirm the requested deletion with its date and how to cancel`() = runUnitTest {
        given()
        val event = scheduled(reminder = false)
        val deletionDate = event.deletesAt.formatLocalized(TimeZone.UTC, Language.EN)

        whenn()
        val push = event.notification.push(Language.EN)
        val email = event.notification.email(Language.EN)
        val text = event.notification.text(Language.EN)

        then()
        assertEquals("Your BookkMe account will be deleted", push.title)
        assertEquals(
            "You asked to delete your account. It will be deleted on $deletionDate. " +
                "Changed your mind? Open the app and cancel the deletion.",
            push.body,
        )
        assertEquals("Your BookkMe account will be deleted on $deletionDate", email.subject)
        assertEquals(
            "Your BookkMe account will be deleted on $deletionDate, as you asked. Open the app to cancel.",
            text.text,
        )
    }

    @Test
    fun `should remind the user of the deletion date and how to cancel`() = runUnitTest {
        given()
        val event = scheduled(reminder = true)
        val deletionDate = event.deletesAt.formatLocalized(TimeZone.UTC, Language.EN)

        whenn()
        val push = event.notification.push(Language.EN)
        val email = event.notification.email(Language.EN)

        then()
        assertEquals("Reminder: your BookkMe account will be deleted", push.title)
        assertEquals("Your account will be deleted on $deletionDate. Open the app to cancel.", push.body)
        assertEquals("Reminder: your BookkMe account will be deleted on $deletionDate", email.subject)
    }

    @Test
    fun `should render the Ukrainian deletion confirmation`() = runUnitTest {
        given()
        val event = scheduled(reminder = false)
        val deletionDate = event.deletesAt.formatLocalized(TimeZone.UTC, Language.UK)

        whenn()
        val email = event.notification.email(Language.UK)

        then()
        assertEquals("Ваш акаунт BookkMe буде видалено $deletionDate", email.subject)
    }

    @Test
    fun `should render the Polish deletion reminder`() = runUnitTest {
        given()
        val event = scheduled(reminder = true)
        val deletionDate = event.deletesAt.formatLocalized(TimeZone.UTC, Language.PL)

        whenn()
        val push = event.notification.push(Language.PL)

        then()
        assertEquals("Twoje konto zostanie usunięte $deletionDate. Otwórz aplikację, aby anulować.", push.body)
    }

    @Test
    fun `should tell the user the deletion was cancelled`() = runUnitTest {
        given()
        val event = AuthEvent.AccountDeletionCancelled(Uuid.random())

        whenn()
        val push = event.notification.push(Language.EN)
        val email = event.notification.email(Language.EN)
        val ukrainian = event.notification.push(Language.UK)

        then()
        assertEquals("Your BookkMe account won't be deleted", push.title)
        assertEquals("You cancelled the deletion of your account. It stays as it is.", push.body)
        assertEquals("Your BookkMe account won't be deleted", email.subject)
        assertEquals("Ваш акаунт BookkMe не буде видалено", ukrainian.title)
    }
}
