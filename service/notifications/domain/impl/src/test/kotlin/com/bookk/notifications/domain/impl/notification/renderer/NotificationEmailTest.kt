package com.bookk.notifications.domain.impl.notification.renderer

import com.bookk.core.domain.entity.Language
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.notifications.domain.impl.notification.EmailNotification
import com.bookk.notifications.domain.impl.notification.NotificationParameters
import com.bookk.notifications.domain.impl.notification.NotificationType
import com.bookk.notifications.domain.impl.notification.PushNotification
import com.bookk.notifications.domain.impl.notification.TextNotification
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class NotificationEmailTest {

    private fun notification(
        type: NotificationType = NotificationType.APPOINTMENT,
        email: (Language) -> EmailNotification = { EmailNotification(subject = "Subject", body = "Body") },
    ) = NotificationParameters(
        type = type,
        push = { PushNotification(title = "Title", body = "Body") },
        email = email,
        text = { TextNotification(text = "Text") },
    )

    @Test
    fun `should render the subject and body in the requested language`() = runUnitTest {
        given()
        val params =
            notification(email = { language ->
                EmailNotification(subject = "Subject $language", body = "Body $language")
            })

        whenn()
        val content = params.emailContent(Language.PL)

        then()
        assertEquals("Subject PL", content.subject)
        assertTrue(content.textBody.startsWith("Body PL"))
        assertTrue(content.htmlBody.contains("Body PL"))
    }

    @Test
    fun `should escape markup from user-entered values in the html body`() = runUnitTest {
        given()
        val params =
            notification(email = {
                EmailNotification(subject = "Subject", body = "<script>alert('x')</script> & \"Salon\"")
            })

        whenn()
        val content = params.emailContent(Language.EN)

        then()
        assertFalse(content.htmlBody.contains("<script>"))
        assertTrue(content.htmlBody.contains("&lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt; &amp; &quot;Salon&quot;"))
        assertTrue(content.textBody.startsWith("<script>alert('x')</script> & \"Salon\""))
    }

    @Test
    fun `should tell the user how to switch off emails they opted into`() = runUnitTest {
        given()
        val params = notification(type = NotificationType.APPOINTMENT)

        whenn()
        val content = params.emailContent(Language.EN)

        then()
        val footer = message(Language.EN, "email.footer.settings")
        assertTrue(content.textBody.endsWith(footer))
        assertTrue(content.htmlBody.contains(footer))
    }

    @Test
    fun `should explain that account notices are sent regardless of email settings`() = runUnitTest {
        given()
        val params = notification(type = NotificationType.ACCOUNT)

        whenn()
        val content = params.emailContent(Language.UK)

        then()
        val footer = message(Language.UK, "email.footer.account")
        assertTrue(content.textBody.endsWith(footer))
        assertTrue(content.htmlBody.contains(footer))
    }

    @Test
    fun `should tag the email with its notification type`() = runUnitTest {
        given()
        val params = notification(type = NotificationType.EMPLOYEE)

        whenn()
        val content = params.emailContent(Language.EN)

        then()
        assertEquals("notification-employee", content.tag)
    }
}
