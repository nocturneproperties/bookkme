package com.bookk.notifications.domain.impl.channel

import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.notifications.domain.datasource.NotificationTargetDataSource
import com.bookk.notifications.domain.impl.notification.EmailNotification
import com.bookk.notifications.domain.impl.notification.NotificationParameters
import com.bookk.notifications.domain.impl.notification.NotificationType
import com.bookk.notifications.domain.impl.notification.PushNotification
import com.bookk.notifications.domain.impl.notification.TextNotification
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

/**
 * [EmailNotificationSender] is still a placeholder — it delivers nothing and reports the failure so
 * [com.bookk.notifications.domain.impl.notification.SendNotification] can surface it. These tests pin that
 * contract; replace them with delivery assertions once the sender is implemented.
 */
internal class EmailNotificationSenderTest {

    private class SutFixture {
        val transactionManager = mockk<TransactionManager>()
        val targetDataSource = mockk<NotificationTargetDataSource>()
        val sut = EmailNotificationSender(transactionManager, targetDataSource)
    }

    private fun notificationParams(type: NotificationType = NotificationType.APPOINTMENT) = NotificationParameters(
        type = type,
        push = { PushNotification(title = "Title", body = "Subtitle") },
        email = { EmailNotification(subject = "Subject", body = "Body") },
        text = { TextNotification(text = "Text") },
    )

    @Test
    fun `should report email delivery as not implemented`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()

        whenn()
        val result = fixture.sut.send(userId, notificationParams())

        then()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is UnsupportedOperationException)
    }

    @Test
    fun `should report email delivery as not implemented for employee notifications`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()

        whenn()
        val result = fixture.sut.send(userId, notificationParams(NotificationType.EMPLOYEE))

        then()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is UnsupportedOperationException)
    }

    @Test
    fun `should not resolve the email target while delivery is not implemented`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()

        whenn()
        fixture.sut.send(userId, notificationParams())

        then()
        coVerify(exactly = 0) { fixture.targetDataSource.getEmail(any()) }
    }
}
