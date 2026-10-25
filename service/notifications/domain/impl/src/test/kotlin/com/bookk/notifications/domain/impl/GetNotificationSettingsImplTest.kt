package com.bookk.notifications.domain.impl

import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.notifications.domain.api.entity.CommunicationChannel
import com.bookk.notifications.domain.api.entity.NotificationChannelSettings
import com.bookk.notifications.domain.api.entity.NotificationSettings
import com.bookk.notifications.domain.datasource.NotificationSettingsDataSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class GetNotificationSettingsImplTest {

    private class SutFixture {
        val notificationSettingsDataSource = mockk<NotificationSettingsDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val sut = GetNotificationSettingsImpl(notificationSettingsDataSource, transactionManager)
    }

    @Test
    fun `should return existing notification settings for user`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val settings = NotificationSettings.stub(
            userId = userId,
            channels = listOf(
                NotificationChannelSettings.stub(channel = CommunicationChannel.EMAIL, enabled = true),
                NotificationChannelSettings.stub(channel = CommunicationChannel.PUSH_NOTIFICATIONS, enabled = false),
                NotificationChannelSettings.stub(channel = CommunicationChannel.TELEGRAM, enabled = false),
            )
        )
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { notificationSettingsDataSource.getByUserId(userId) } returns settings
        }

        whenn()
        val result = fixture.sut.invoke(userId)

        then()
        assertTrue(result.isSuccess)
        assertEquals(settings, result.getOrNull())
        coVerify(exactly = 0) { fixture.notificationSettingsDataSource.upsert(any<NotificationSettings>()) }
    }

    @Test
    fun `should create default settings with all channels disabled when none exist`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val defaultSettings = NotificationSettings.stub(userId = userId, appointmentEnabled = false, channels = listOf(
            NotificationChannelSettings.stub(channel = CommunicationChannel.PUSH_NOTIFICATIONS, enabled = false),
            NotificationChannelSettings.stub(channel = CommunicationChannel.EMAIL, enabled = false),
            NotificationChannelSettings.stub(channel = CommunicationChannel.TELEGRAM, enabled = false),
        ))
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { notificationSettingsDataSource.getByUserId(userId) } returns null
            coEvery { notificationSettingsDataSource.upsert(any<NotificationSettings>()) } returns defaultSettings
        }

        whenn()
        val result = fixture.sut.invoke(userId)

        then()
        assertTrue(result.isSuccess)
        assertEquals(defaultSettings, result.getOrNull())
        assertTrue(result.getOrNull()!!.channels.none { it.enabled })
        coVerify(exactly = 1) {
            fixture.notificationSettingsDataSource.upsert(
                match<NotificationSettings> { s ->
                    s.userId == userId && !s.appointmentEnabled && s.channels.none { ch -> ch.enabled }
                }
            )
        }
    }

    @Test
    fun `should only return channels available to clients`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val settings = NotificationSettings.stub(
            userId = userId,
            channels = listOf(
                NotificationChannelSettings.stub(channel = CommunicationChannel.EMAIL, availableToClients = true),
                NotificationChannelSettings.stub(channel = CommunicationChannel.PUSH_NOTIFICATIONS, availableToClients = false),
                NotificationChannelSettings.stub(channel = CommunicationChannel.TELEGRAM, availableToClients = true),
            )
        )
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { notificationSettingsDataSource.getByUserId(userId) } returns settings
        }

        whenn()
        val result = fixture.sut.invoke(userId)

        then()
        assertTrue(result.isSuccess)
        val channels = result.getOrNull()!!.channels
        assertEquals(2, channels.size)
        assertTrue(channels.all { it.availableToClients })
        assertTrue(channels.none { it.channel == CommunicationChannel.PUSH_NOTIFICATIONS })
    }

    @Test
    fun `should return failure when datasource throws`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { notificationSettingsDataSource.getByUserId(userId) } throws RuntimeException("db error")
        }

        whenn()
        val result = fixture.sut.invoke(userId)

        then()
        assertTrue(result.isFailure)
    }
}
