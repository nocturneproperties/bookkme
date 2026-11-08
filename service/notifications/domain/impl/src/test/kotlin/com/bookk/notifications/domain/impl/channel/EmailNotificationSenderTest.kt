package com.bookk.notifications.domain.impl.channel

import com.bookk.core.domain.datasource.transaction.mockedTransactionManager
import com.bookk.core.domain.entity.Language
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.notifications.domain.datasource.DeviceDataSource
import com.bookk.notifications.domain.datasource.NotificationTargetDataSource
import com.bookk.notifications.domain.impl.notification.EmailNotification
import com.bookk.notifications.domain.impl.notification.NotificationParameters
import com.bookk.notifications.domain.impl.notification.NotificationType
import com.bookk.notifications.domain.impl.notification.PushNotification
import com.bookk.notifications.domain.impl.notification.TextNotification
import com.bookk.notifications.domain.impl.notification.renderer.emailContent
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import library.email.EmailMessage
import library.email.EmailSendError
import library.email.EmailSender
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class EmailNotificationSenderTest {

    private class SutFixture {
        val transactionManager = mockedTransactionManager()
        val targetDataSource = mockk<NotificationTargetDataSource>()
        val deviceDataSource = mockk<DeviceDataSource>()
        val emailSender = mockk<EmailSender>()
        val sut = EmailNotificationSender(transactionManager, targetDataSource, deviceDataSource, emailSender)
    }

    private fun notificationParams(type: NotificationType = NotificationType.APPOINTMENT) = NotificationParameters(
        type = type,
        push = { PushNotification(title = "Title", body = "Subtitle") },
        email = { language -> EmailNotification(subject = "Subject $language", body = "Body $language") },
        text = { TextNotification(text = "Text") },
    )

    @Test
    fun `should email the stored address in the language the user last used`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val params = notificationParams()
        val sent = slot<EmailMessage>()
        with(fixture) {
            coEvery { targetDataSource.getEmail(userId) } returns "client@example.com"
            coEvery { deviceDataSource.getLastUsedLanguage(userId) } returns Language.PL
            coEvery { emailSender.send(capture(sent)) } returns Result.success(Unit)
        }

        whenn()
        val result = fixture.sut.send(userId, params)

        then()
        assertTrue(result.isSuccess)
        assertEquals(EmailMessage("client@example.com", params.emailContent(Language.PL)), sent.captured)
    }

    @Test
    fun `should email in English when the user has no devices`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val params = notificationParams()
        val sent = slot<EmailMessage>()
        with(fixture) {
            coEvery { targetDataSource.getEmail(userId) } returns "client@example.com"
            coEvery { deviceDataSource.getLastUsedLanguage(userId) } returns null
            coEvery { emailSender.send(capture(sent)) } returns Result.success(Unit)
        }

        whenn()
        fixture.sut.send(userId, params)

        then()
        assertEquals(params.emailContent(Language.EN), sent.captured.content)
    }

    @Test
    fun `should send nothing when no address is stored for the user`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        coEvery { fixture.targetDataSource.getEmail(userId) } returns null

        whenn()
        val result = fixture.sut.send(userId, notificationParams())

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { fixture.emailSender.send(any()) }
    }

    @Test
    fun `should report a failed delivery`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        with(fixture) {
            coEvery { targetDataSource.getEmail(userId) } returns "bounced@example.com"
            coEvery { deviceDataSource.getLastUsedLanguage(userId) } returns Language.EN
            coEvery { emailSender.send(any()) } returns Result.failure(EmailSendError.Undeliverable())
        }

        whenn()
        val result = fixture.sut.send(userId, notificationParams())

        then()
        assertTrue(result.exceptionOrNull() is EmailSendError.Undeliverable)
    }

    @Test
    fun `should read the address and language inside a transaction`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val reads = mutableListOf<Boolean>()
        with(fixture) {
            coEvery { targetDataSource.getEmail(userId) } coAnswers {
                reads += transactionManager.isInTransaction()
                "client@example.com"
            }
            coEvery { deviceDataSource.getLastUsedLanguage(userId) } coAnswers {
                reads += transactionManager.isInTransaction()
                Language.EN
            }
            coEvery { emailSender.send(any()) } returns Result.success(Unit)
        }

        whenn()
        fixture.sut.send(userId, notificationParams())

        then()
        assertEquals(listOf(true, true), reads)
    }

    @Test
    fun `should send the email outside the transaction`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        var sentInTransaction = true
        with(fixture) {
            coEvery { targetDataSource.getEmail(userId) } returns "client@example.com"
            coEvery { deviceDataSource.getLastUsedLanguage(userId) } returns Language.EN
            coEvery { emailSender.send(any()) } coAnswers {
                sentInTransaction = transactionManager.isInTransaction()
                Result.success(Unit)
            }
        }

        whenn()
        fixture.sut.send(userId, notificationParams())

        then()
        assertFalse(sentInTransaction)
    }
}
