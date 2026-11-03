package com.bookk.notifications.domain.impl

import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.domain.entity.Language
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.notifications.client.api.export.NotificationsUserDataExport
import com.bookk.notifications.client.api.export.NotificationsUserDataExport.ChannelExport
import com.bookk.notifications.client.api.export.NotificationsUserDataExport.DeviceExport
import com.bookk.notifications.client.api.export.NotificationsUserDataExport.SettingsExport
import com.bookk.notifications.domain.api.entity.CommunicationChannel
import com.bookk.notifications.domain.api.entity.Device
import com.bookk.notifications.domain.api.entity.NotificationChannelSettings
import com.bookk.notifications.domain.api.entity.NotificationSettings
import com.bookk.notifications.domain.datasource.DeviceDataSource
import com.bookk.notifications.domain.datasource.NotificationSettingsDataSource
import com.bookk.notifications.domain.datasource.NotificationTargetDataSource
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class ExportUserNotificationDataImplTest {

    private class SutFixture {
        val settingsDataSource = mockk<NotificationSettingsDataSource>()
        val deviceDataSource = mockk<DeviceDataSource>()
        val targetDataSource = mockk<NotificationTargetDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val sut = ExportUserNotificationDataImpl(settingsDataSource, deviceDataSource, targetDataSource, transactionManager)
        val userId = Uuid.random()
        val readInTransaction = mutableListOf<Boolean>()

        init {
            transactionManager.mockTransaction()
            coEvery { settingsDataSource.getByUserId(userId) } coAnswers {
                readInTransaction += transactionManager.isInTransaction()
                null
            }
            coEvery { deviceDataSource.getByUserId(userId) } returns emptyList()
            coEvery { targetDataSource.getEmail(userId) } returns null
            coEvery { targetDataSource.getTelegram(userId) } returns null
        }
    }

    @Test
    fun `should export the user's settings, devices and targets`() = runUnitTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.settingsDataSource.getByUserId(fixture.userId) } returns NotificationSettings.stub(userId = fixture.userId).copy(
            appointmentEnabled = true,
            channels = listOf(NotificationChannelSettings.stub(channel = CommunicationChannel.PUSH_NOTIFICATIONS, enabled = true)),
        )
        coEvery { fixture.deviceDataSource.getByUserId(fixture.userId) } returns listOf(
            Device.stub(userId = fixture.userId, notificationToken = "fcm-token", language = Language.UK),
            Device.stub(userId = fixture.userId, notificationToken = null, language = Language.EN),
        )
        coEvery { fixture.targetDataSource.getEmail(fixture.userId) } returns "olena@example.com"
        coEvery { fixture.targetDataSource.getTelegram(fixture.userId) } returns "@olena"

        whenn()
        val result = fixture.sut(fixture.userId)

        then()
        assertEquals(
            NotificationsUserDataExport(
                settings = SettingsExport(appointmentNotificationsEnabled = true, channels = listOf(ChannelExport("PUSH_NOTIFICATIONS", enabled = true))),
                devices = listOf(DeviceExport("UK", pushNotificationsRegistered = true), DeviceExport("EN", pushNotificationsRegistered = false)),
                email = "olena@example.com",
                telegram = "@olena",
            ),
            result.getOrNull(),
        )
    }

    @Test
    fun `should export no settings for a user who never set any`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val result = fixture.sut(fixture.userId)

        then()
        assertEquals(NotificationsUserDataExport.stub(), result.getOrNull())
        assertEquals(listOf(true), fixture.readInTransaction)
    }

    @Test
    fun `should fail when the settings can't be read`() = runUnitTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.settingsDataSource.getByUserId(fixture.userId) } throws IllegalStateException("database unreachable")

        whenn()
        val result = fixture.sut(fixture.userId)

        then()
        assertTrue(result.isFailure)
    }
}
