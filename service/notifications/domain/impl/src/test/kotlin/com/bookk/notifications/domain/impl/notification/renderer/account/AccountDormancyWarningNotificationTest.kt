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

internal class AccountDormancyWarningNotificationTest {

    private fun event(final: Boolean = false) = AuthEvent.AccountDormancyWarning(
        userId = Uuid.random(),
        deletesAt = Instant.parse("2029-03-05T14:30:00Z"),
        final = final,
    )

    @Test
    fun `should be an account notification the user cannot switch off`() = runUnitTest {
        given()
        val event = event()

        whenn()
        val type = event.notification.type

        then()
        assertEquals(NotificationType.ACCOUNT, type)
    }

    @Test
    fun `should render the English first warning with the deletion date`() = runUnitTest {
        given()
        val event = event()
        val deletionDate = event.deletesAt.formatLocalized(TimeZone.UTC, Language.EN)

        whenn()
        val push = event.notification.push(Language.EN)
        val email = event.notification.email(Language.EN)

        then()
        assertEquals("Your Bookk account will be deleted", push.title)
        assertEquals(
            "You haven't used Bookk for almost 3 years, so your account will be deleted on $deletionDate. Open the app to keep it.",
            push.body,
        )
        assertEquals("Your Bookk account will be deleted on $deletionDate", email.subject)
    }

    @Test
    fun `should render the Ukrainian first warning with the deletion date`() = runUnitTest {
        given()
        val event = event()
        val deletionDate = event.deletesAt.formatLocalized(TimeZone.UTC, Language.UK)

        whenn()
        val push = event.notification.push(Language.UK)

        then()
        assertEquals("Ваш акаунт Bookk буде видалено", push.title)
        assertEquals(
            "Ви не користувалися Bookk майже 3 роки, тож ваш акаунт буде видалено $deletionDate. Відкрийте застосунок, щоб зберегти його.",
            push.body,
        )
    }

    @Test
    fun `should render the final warning as the last reminder`() = runUnitTest {
        given()
        val event = event(final = true)
        val deletionDate = event.deletesAt.formatLocalized(TimeZone.UTC, Language.EN)

        whenn()
        val push = event.notification.push(Language.EN)
        val text = event.notification.text(Language.EN)

        then()
        assertEquals("Last reminder: your Bookk account will be deleted", push.title)
        assertEquals(
            "Last reminder: your Bookk account will be deleted on $deletionDate. Sign in to keep it.",
            text.text,
        )
    }

    @Test
    fun `should render the Ukrainian final warning`() = runUnitTest {
        given()
        val event = event(final = true)
        val deletionDate = event.deletesAt.formatLocalized(TimeZone.UTC, Language.UK)

        whenn()
        val email = event.notification.email(Language.UK)

        then()
        assertEquals("Останнє нагадування: акаунт Bookk буде видалено $deletionDate", email.subject)
    }
}
