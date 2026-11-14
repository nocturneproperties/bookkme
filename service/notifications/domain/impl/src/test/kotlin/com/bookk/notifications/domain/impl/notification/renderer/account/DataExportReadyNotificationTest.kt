package com.bookk.notifications.domain.impl.notification.renderer.account

import com.bookk.core.domain.entity.Language
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.notifications.domain.impl.notification.NotificationType
import com.bookk.notifications.domain.impl.notification.renderer.formatLocalized
import com.bookk.server.business.client.api.event.BusinessEvent
import com.bookk.server.user.client.api.event.UserEvent
import kotlinx.datetime.TimeZone
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class DataExportReadyNotificationTest {

    private val expiresAt = Instant.parse("2026-11-06T14:30:00Z")
    private val personal = UserEvent.DataExportReady(userId = Uuid.random(), expiresAt = expiresAt)
    private val business = BusinessEvent.ExportReady(
        businessId = Uuid.random(),
        ownerUserId = Uuid.random(),
        expiresAt = expiresAt,
    )

    @Test
    fun `should be account notifications the user cannot switch off`() = runUnitTest {
        given()
        val notifications = listOf(personal.notification, business.notification)

        whenn()
        val types = notifications.map { it.type }

        then()
        assertEquals(listOf(NotificationType.ACCOUNT, NotificationType.ACCOUNT), types)
    }

    @Test
    fun `should tell the user in English that their data is ready until when`() = runUnitTest {
        given()
        val until = expiresAt.formatLocalized(TimeZone.UTC, Language.EN)

        whenn()
        val push = personal.notification.push(Language.EN)
        val email = personal.notification.email(Language.EN)

        then()
        assertEquals("Your data export is ready", push.title)
        assertEquals("Download it in the app before $until, when it is deleted.", push.body)
        assertEquals("Your Bookk data export is ready", email.subject)
    }

    @Test
    fun `should tell the user in Ukrainian that their data is ready until when`() = runUnitTest {
        given()
        val until = expiresAt.formatLocalized(TimeZone.UTC, Language.UK)

        whenn()
        val push = personal.notification.push(Language.UK)

        then()
        assertEquals("Ваші дані готові до завантаження", push.title)
        assertEquals("Завантажте їх у застосунку до $until, після цього файл буде видалено.", push.body)
    }

    @Test
    fun `should tell the owner in English that the business's data is ready until when`() = runUnitTest {
        given()
        val until = expiresAt.formatLocalized(TimeZone.UTC, Language.EN)

        whenn()
        val push = business.notification.push(Language.EN)
        val text = business.notification.text(Language.EN)

        then()
        assertEquals("Your business data export is ready", push.title)
        assertEquals("Download it in the app before $until, when it is deleted.", push.body)
        assertEquals("Your Bookk business data export is ready. Download it in the app before $until.", text.text)
    }

    @Test
    fun `should tell the owner in Ukrainian that the business's data is ready`() = runUnitTest {
        given()

        whenn()
        val push = business.notification.push(Language.UK)

        then()
        assertEquals("Дані вашого бізнесу готові до завантаження", push.title)
    }

    @Test
    fun `should tell the user in Polish that their data is ready until when`() = runUnitTest {
        given()
        val until = expiresAt.formatLocalized(TimeZone.UTC, Language.PL)

        whenn()
        val push = personal.notification.push(Language.PL)

        then()
        assertEquals("Twoje dane są gotowe do pobrania", push.title)
        assertEquals("Pobierz je w aplikacji do $until, potem plik zostanie usunięty.", push.body)
    }
}
