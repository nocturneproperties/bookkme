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

internal class UpdateNotificationSettingsImplTest {

    private class SutFixture {
        val notificationSettingsDataSource = mockk<NotificationSettingsDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val sut = UpdateNotificationSettingsImpl(notificationSettingsDataSource, transactionManager)
    }

    @Test
    fun `should update notification settings with channel enabled states`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val settingsId = Uuid.random()
        val channels = listOf(
            NotificationChannelSettings.stub(channel = CommunicationChannel.EMAIL, enabled = true),
            NotificationChannelSettings.stub(channel = CommunicationChannel.TELEGRAM, enabled = false),
            NotificationChannelSettings.stub(channel = CommunicationChannel.PUSH_NOTIFICATIONS, enabled = true),
        )
        val update = NotificationSettings.Update(id = settingsId, appointmentEnabled = false, channels = channels)
        val expectedSettings = NotificationSettings.stub(id = settingsId, userId = userId, appointmentEnabled = false, channels = channels)
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { notificationSettingsDataSource.upsert(any<NotificationSettings>()) } returns expectedSettings
        }

        whenn()
        val result = fixture.sut.invoke(userId, update)

        then()
        assertTrue(result.isSuccess)
        assertEquals(expectedSettings, result.getOrNull())
        coVerify(exactly = 1) { fixture.notificationSettingsDataSource.upsert(any<NotificationSettings>()) }
    }

    @Test
    fun `should return failure when datasource throws`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val update = NotificationSettings.Update(id = Uuid.random(), appointmentEnabled = true, channels = emptyList())
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { notificationSettingsDataSource.upsert(any<NotificationSettings>()) } throws RuntimeException("db error")
        }

        whenn()
        val result = fixture.sut.invoke(userId, update)

        then()
        assertTrue(result.isFailure)
    }
}
