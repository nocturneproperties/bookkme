package com.bookk.notifications.domain.impl.channel

import com.bookk.core.domain.datasource.transaction.mockedTransactionManager
import com.bookk.core.domain.entity.Language
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.notifications.domain.api.entity.CommunicationChannel
import com.bookk.notifications.domain.api.entity.NotificationChannelSettings
import com.bookk.notifications.domain.api.entity.NotificationSettings
import com.bookk.notifications.domain.datasource.DeviceDataSource
import com.bookk.notifications.domain.datasource.NotificationSettingsDataSource
import com.bookk.notifications.domain.datasource.NotificationTargetDataSource
import com.bookk.notifications.domain.impl.channel.telegram.TelegramDelivery
import com.bookk.notifications.domain.impl.channel.telegram.TelegramMessenger
import com.bookk.notifications.domain.impl.notification.EmailNotification
import com.bookk.notifications.domain.impl.notification.NotificationParameters
import com.bookk.notifications.domain.impl.notification.NotificationType
import com.bookk.notifications.domain.impl.notification.PushNotification
import com.bookk.notifications.domain.impl.notification.TextNotification
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class TelegramNotificationSenderTest {

    private class SutFixture {
        val transactionManager = mockedTransactionManager()
        val targetDataSource = mockk<NotificationTargetDataSource>()
        val deviceDataSource = mockk<DeviceDataSource>()
        val notificationSettingsDataSource = mockk<NotificationSettingsDataSource>()
        val telegramMessenger = mockk<TelegramMessenger>()
        val sut = TelegramNotificationSender(
            transactionManager,
            targetDataSource,
            deviceDataSource,
            notificationSettingsDataSource,
            telegramMessenger,
        )
    }

    private fun notificationParams() = NotificationParameters(
        type = NotificationType.APPOINTMENT,
        push = { PushNotification(title = "Title", body = "Subtitle") },
        email = { EmailNotification(subject = "Subject", body = "Body") },
        text = { language -> TextNotification(text = "Text $language") },
    )

    @Test
    fun `should send the text to the user's Telegram chat in the language they last used`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        with(fixture) {
            coEvery { targetDataSource.getTelegram(userId) } returns "123456789"
            coEvery { deviceDataSource.getLastUsedLanguage(userId) } returns Language.UK
            coEvery { telegramMessenger.send(123456789, "Text UK") } returns TelegramDelivery.SENT
        }

        whenn()
        val result = fixture.sut.send(userId, notificationParams())

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { fixture.telegramMessenger.send(123456789, "Text UK") }
    }

    @Test
    fun `should send in English when the user has no devices`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        with(fixture) {
            coEvery { targetDataSource.getTelegram(userId) } returns "123456789"
            coEvery { deviceDataSource.getLastUsedLanguage(userId) } returns null
            coEvery { telegramMessenger.send(123456789, "Text EN") } returns TelegramDelivery.SENT
        }

        whenn()
        fixture.sut.send(userId, notificationParams())

        then()
        coVerify(exactly = 1) { fixture.telegramMessenger.send(123456789, "Text EN") }
    }

    @Test
    fun `should send nothing when no Telegram chat is stored for the user`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        coEvery { fixture.targetDataSource.getTelegram(userId) } returns null

        whenn()
        val result = fixture.sut.send(userId, notificationParams())

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { fixture.telegramMessenger.send(any(), any()) }
    }

    @Test
    fun `should read the chat inside a transaction and call Telegram outside it`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        var readInTransaction = false
        var sentInTransaction = true
        with(fixture) {
            coEvery { targetDataSource.getTelegram(userId) } coAnswers {
                readInTransaction = transactionManager.isInTransaction()
                "123456789"
            }
            coEvery { deviceDataSource.getLastUsedLanguage(userId) } returns Language.EN
            coEvery { telegramMessenger.send(any(), any()) } coAnswers {
                sentInTransaction = transactionManager.isInTransaction()
                TelegramDelivery.SENT
            }
        }

        whenn()
        fixture.sut.send(userId, notificationParams())

        then()
        assertTrue(readInTransaction)
        assertFalse(sentInTransaction)
    }

    @Test
    fun `should turn the Telegram channel off when the user's chat is unreachable`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val telegram = NotificationChannelSettings.stub(channel = CommunicationChannel.TELEGRAM, enabled = true)
        var disabledInTransaction = false
        with(fixture) {
            coEvery { targetDataSource.getTelegram(userId) } returns "123456789"
            coEvery { deviceDataSource.getLastUsedLanguage(userId) } returns Language.EN
            coEvery { telegramMessenger.send(any(), any()) } returns TelegramDelivery.CHAT_UNREACHABLE
            coEvery { notificationSettingsDataSource.getByUserId(userId) } returns
                NotificationSettings.stub(userId = userId, channels = listOf(telegram))
            coEvery { notificationSettingsDataSource.upsert(any<NotificationChannelSettings>()) } coAnswers {
                disabledInTransaction = transactionManager.isInTransaction()
            }
        }

        whenn()
        val result = fixture.sut.send(userId, notificationParams())

        then()
        assertTrue(result.isSuccess)
        assertTrue(disabledInTransaction)
        coVerify(exactly = 1) { fixture.notificationSettingsDataSource.upsert(telegram.copy(enabled = false)) }
    }

    @Test
    fun `should leave the settings alone when the unreachable chat's channel is already off`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val telegram = NotificationChannelSettings.stub(channel = CommunicationChannel.TELEGRAM, enabled = false)
        with(fixture) {
            coEvery { targetDataSource.getTelegram(userId) } returns "123456789"
            coEvery { deviceDataSource.getLastUsedLanguage(userId) } returns Language.EN
            coEvery { telegramMessenger.send(any(), any()) } returns TelegramDelivery.CHAT_UNREACHABLE
            coEvery { notificationSettingsDataSource.getByUserId(userId) } returns
                NotificationSettings.stub(userId = userId, channels = listOf(telegram))
        }

        whenn()
        val result = fixture.sut.send(userId, notificationParams())

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { fixture.notificationSettingsDataSource.upsert(any<NotificationChannelSettings>()) }
    }

    @Test
    fun `should leave the settings alone when the message was sent`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        with(fixture) {
            coEvery { targetDataSource.getTelegram(userId) } returns "123456789"
            coEvery { deviceDataSource.getLastUsedLanguage(userId) } returns Language.EN
            coEvery { telegramMessenger.send(any(), any()) } returns TelegramDelivery.SENT
        }

        whenn()
        fixture.sut.send(userId, notificationParams())

        then()
        coVerify(exactly = 0) { fixture.notificationSettingsDataSource.getByUserId(any()) }
        coVerify(exactly = 0) { fixture.notificationSettingsDataSource.upsert(any<NotificationChannelSettings>()) }
    }

    @Test
    fun `should leave the settings alone when Telegram delivery is not configured`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        with(fixture) {
            coEvery { targetDataSource.getTelegram(userId) } returns "123456789"
            coEvery { deviceDataSource.getLastUsedLanguage(userId) } returns Language.EN
            coEvery { telegramMessenger.send(any(), any()) } returns TelegramDelivery.DROPPED
        }

        whenn()
        val result = fixture.sut.send(userId, notificationParams())

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { fixture.notificationSettingsDataSource.upsert(any<NotificationChannelSettings>()) }
    }

    @Test
    fun `should report a failure when the Telegram call fails`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        with(fixture) {
            coEvery { targetDataSource.getTelegram(userId) } returns "123456789"
            coEvery { deviceDataSource.getLastUsedLanguage(userId) } returns Language.EN
            coEvery { telegramMessenger.send(any(), any()) } throws IllegalStateException("Bad Request")
        }

        whenn()
        val result = fixture.sut.send(userId, notificationParams())

        then()
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        coVerify(exactly = 0) { fixture.notificationSettingsDataSource.upsert(any<NotificationChannelSettings>()) }
    }

    @Test
    fun `should send nothing when the stored chat id is not a number`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        with(fixture) {
            coEvery { targetDataSource.getTelegram(userId) } returns "@someone"
            coEvery { deviceDataSource.getLastUsedLanguage(userId) } returns Language.EN
        }

        whenn()
        val result = fixture.sut.send(userId, notificationParams())

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { fixture.telegramMessenger.send(any(), any()) }
    }
}
