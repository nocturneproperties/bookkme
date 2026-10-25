package com.bookk.notifications.domain.impl.notification

import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.notifications.domain.api.entity.CommunicationChannel
import com.bookk.notifications.domain.api.entity.NotificationChannelSettings
import com.bookk.notifications.domain.api.entity.NotificationSettings
import com.bookk.notifications.domain.datasource.NotificationSettingsDataSource
import com.bookk.notifications.domain.impl.channel.NotificationSender
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class SendNotificationTest {

    private class SutFixture {
        val notificationDataSource = mockk<NotificationSettingsDataSource>()
        val emailSender = mockk<NotificationSender>()
        val telegramSender = mockk<NotificationSender>()
        val pushSender = mockk<NotificationSender>()
        val senderMap = mapOf(
            CommunicationChannel.EMAIL to emailSender,
            CommunicationChannel.TELEGRAM to telegramSender,
            CommunicationChannel.PUSH_NOTIFICATIONS to pushSender,
        )
        val sut = SendNotification(notificationDataSource, senderMap)
    }

    private fun notificationParams() = NotificationParameters(
        type = NotificationType.APPOINTMENT,
        push = { PushNotification(title = "Title", body = "Subtitle") },
        email = { EmailNotification(subject = "Subject", body = "Body") },
        text = { TextNotification(text = "Text") },
    )

    private fun NotificationParameters.copyWithType(type: NotificationType) =
        NotificationParameters(type = type, push = push, email = email, text = text)

    @Test
    fun `should send to all enabled channels`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val notification = notificationParams()
        val settings = NotificationSettings.stub(
            userId = userId,
            channels = listOf(
                NotificationChannelSettings.stub(channel = CommunicationChannel.EMAIL, enabled = true),
                NotificationChannelSettings.stub(channel = CommunicationChannel.TELEGRAM, enabled = true),
                NotificationChannelSettings.stub(channel = CommunicationChannel.PUSH_NOTIFICATIONS, enabled = false),
            )
        )
        with(fixture) {
            coEvery { notificationDataSource.getByUserId(userId) } returns settings
            coEvery { emailSender.send(userId, notification) } returns Result.success(Unit)
            coEvery { telegramSender.send(userId, notification) } returns Result.success(Unit)
        }

        whenn()
        val result = fixture.sut.invoke(userId, notification)

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { fixture.emailSender.send(userId, notification) }
        coVerify(exactly = 1) { fixture.telegramSender.send(userId, notification) }
        coVerify(exactly = 0) { fixture.pushSender.send(any(), any()) }
    }

    @Test
    fun `should not send when all channels are disabled`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val settings = NotificationSettings.stub(
            userId = userId,
            channels = listOf(
                NotificationChannelSettings.stub(channel = CommunicationChannel.EMAIL, enabled = false),
                NotificationChannelSettings.stub(channel = CommunicationChannel.TELEGRAM, enabled = false),
                NotificationChannelSettings.stub(channel = CommunicationChannel.PUSH_NOTIFICATIONS, enabled = false),
            )
        )
        with(fixture) {
            coEvery { notificationDataSource.getByUserId(userId) } returns settings
        }

        whenn()
        val result = fixture.sut.invoke(userId, notificationParams())

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { fixture.emailSender.send(any(), any()) }
        coVerify(exactly = 0) { fixture.telegramSender.send(any(), any()) }
        coVerify(exactly = 0) { fixture.pushSender.send(any(), any()) }
    }

    @Test
    fun `should not send when notification type is disabled in settings`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val settings = NotificationSettings.stub(
            userId = userId,
            appointmentEnabled = false,
            channels = listOf(
                NotificationChannelSettings.stub(channel = CommunicationChannel.EMAIL, enabled = true),
                NotificationChannelSettings.stub(channel = CommunicationChannel.TELEGRAM, enabled = true),
                NotificationChannelSettings.stub(channel = CommunicationChannel.PUSH_NOTIFICATIONS, enabled = true),
            )
        )
        with(fixture) {
            coEvery { notificationDataSource.getByUserId(userId) } returns settings
        }

        whenn()
        val result = fixture.sut.invoke(userId, notificationParams())

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { fixture.emailSender.send(any(), any()) }
        coVerify(exactly = 0) { fixture.telegramSender.send(any(), any()) }
        coVerify(exactly = 0) { fixture.pushSender.send(any(), any()) }
    }

    @Test
    fun `should send employee notifications even when appointment notifications are disabled`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val notification = notificationParams().copyWithType(NotificationType.EMPLOYEE)
        val settings = NotificationSettings.stub(
            userId = userId,
            appointmentEnabled = false,
            channels = listOf(
                NotificationChannelSettings.stub(channel = CommunicationChannel.EMAIL, enabled = true),
                NotificationChannelSettings.stub(channel = CommunicationChannel.PUSH_NOTIFICATIONS, enabled = false),
            )
        )
        with(fixture) {
            coEvery { notificationDataSource.getByUserId(userId) } returns settings
            coEvery { emailSender.send(userId, notification) } returns Result.success(Unit)
        }

        whenn()
        val result = fixture.sut.invoke(userId, notification)

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { fixture.emailSender.send(userId, notification) }
        coVerify(exactly = 0) { fixture.pushSender.send(any(), any()) }
    }

    @Test
    fun `should not send employee notifications through disabled channels`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val notification = notificationParams().copyWithType(NotificationType.EMPLOYEE)
        val settings = NotificationSettings.stub(
            userId = userId,
            channels = listOf(
                NotificationChannelSettings.stub(channel = CommunicationChannel.EMAIL, enabled = false),
                NotificationChannelSettings.stub(channel = CommunicationChannel.TELEGRAM, enabled = false),
                NotificationChannelSettings.stub(channel = CommunicationChannel.PUSH_NOTIFICATIONS, enabled = false),
            )
        )
        with(fixture) {
            coEvery { notificationDataSource.getByUserId(userId) } returns settings
        }

        whenn()
        val result = fixture.sut.invoke(userId, notification)

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { fixture.emailSender.send(any(), any()) }
        coVerify(exactly = 0) { fixture.telegramSender.send(any(), any()) }
        coVerify(exactly = 0) { fixture.pushSender.send(any(), any()) }
    }

    @Test
    fun `should return failure when settings not found`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        with(fixture) {
            coEvery { notificationDataSource.getByUserId(userId) } returns null
        }

        whenn()
        val result = fixture.sut.invoke(userId, notificationParams())

        then()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }
}
