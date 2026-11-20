package com.bookk.notifications.domain.impl

import com.bookk.core.domain.datasource.transaction.mockedTransactionManager
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.notifications.domain.api.entity.CommunicationChannel
import com.bookk.notifications.domain.api.entity.NotificationChannelSettings
import com.bookk.notifications.domain.api.entity.NotificationSettings
import com.bookk.notifications.domain.api.entity.NotificationSettingsUpdate
import com.bookk.notifications.domain.datasource.NotificationSettingsDataSource
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class UpdateNotificationSettingsImplTest {

    private class SutFixture {
        val notificationSettingsDataSource = mockk<NotificationSettingsDataSource>()
        val transactionManager = mockedTransactionManager()
        val sut = UpdateNotificationSettingsImpl(notificationSettingsDataSource, transactionManager)
        val saved = slot<NotificationSettings>()

        fun storedSettings(settings: NotificationSettings?) {
            coEvery { notificationSettingsDataSource.getByUserId(any()) } returns settings
            coEvery { notificationSettingsDataSource.upsert(capture(saved)) } answers { firstArg() }
        }
    }

    @Test
    fun `should save the requested state of each offered channel and the appointment switch`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val email = NotificationChannelSettings.stub(channel = CommunicationChannel.EMAIL, enabled = false)
        val push = NotificationChannelSettings.stub(channel = CommunicationChannel.PUSH_NOTIFICATIONS, enabled = true)
        val stored = NotificationSettings.stub(
            userId = userId,
            appointmentEnabled = false,
            channels = listOf(email, push),
        )
        val update = NotificationSettingsUpdate(
            id = stored.id,
            appointmentEnabled = true,
            channels = listOf(email.copy(enabled = true), push.copy(enabled = false)),
        )
        fixture.storedSettings(stored)

        whenn()
        val result = fixture.sut.invoke(userId, update)

        then()
        val expected = stored.copy(
            appointmentEnabled = true,
            channels = listOf(email.copy(enabled = true), push.copy(enabled = false)),
        )
        assertEquals(expected, fixture.saved.captured)
        assertEquals(expected, result.getOrNull())
    }

    @Test
    fun `should match channels by type and keep the stored ids`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val email = NotificationChannelSettings.stub(channel = CommunicationChannel.EMAIL, enabled = false)
        val stored = NotificationSettings.stub(userId = userId, channels = listOf(email))
        val update = NotificationSettingsUpdate(
            id = Uuid.random(),
            appointmentEnabled = true,
            channels = listOf(NotificationChannelSettings.stub(channel = CommunicationChannel.EMAIL, enabled = true)),
        )
        fixture.storedSettings(stored)

        whenn()
        fixture.sut.invoke(userId, update)

        then()
        assertEquals(stored.id, fixture.saved.captured.id)
        assertEquals(listOf(email.copy(enabled = true)), fixture.saved.captured.channels)
    }

    @Test
    fun `should keep a channel unchanged when the request leaves it out`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val push = NotificationChannelSettings.stub(channel = CommunicationChannel.PUSH_NOTIFICATIONS, enabled = true)
        val stored = NotificationSettings.stub(userId = userId, channels = listOf(push))
        fixture.storedSettings(stored)

        whenn()
        fixture.sut.invoke(userId, NotificationSettingsUpdate(stored.id, appointmentEnabled = true, emptyList()))

        then()
        assertEquals(listOf(push), fixture.saved.captured.channels)
    }

    @Test
    fun `should not turn on or offer a channel the user is not offered`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val telegram = NotificationChannelSettings.stub(
            channel = CommunicationChannel.TELEGRAM,
            enabled = false,
            availableToClients = false,
        )
        val stored = NotificationSettings.stub(userId = userId, channels = listOf(telegram))
        val update = NotificationSettingsUpdate(
            id = stored.id,
            appointmentEnabled = true,
            channels = listOf(telegram.copy(enabled = true, availableToClients = true)),
        )
        fixture.storedSettings(stored)

        whenn()
        val result = fixture.sut.invoke(userId, update)

        then()
        assertEquals(listOf(telegram), fixture.saved.captured.channels)
        assertEquals(emptyList<NotificationChannelSettings>(), result.getOrNull()?.channels)
    }

    @Test
    fun `should create the default settings when the user has none`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val update = NotificationSettingsUpdate(
            id = Uuid.random(),
            appointmentEnabled = true,
            channels = listOf(NotificationChannelSettings.stub(channel = CommunicationChannel.EMAIL, enabled = true)),
        )
        fixture.storedSettings(null)

        whenn()
        val result = fixture.sut.invoke(userId, update)

        then()
        assertTrue(result.isSuccess)
        val saved = fixture.saved.captured
        assertEquals(userId, saved.userId)
        assertTrue(saved.appointmentEnabled)
        assertTrue(saved.channels.single { it.channel == CommunicationChannel.EMAIL }.enabled)
        assertFalse(saved.channels.single { it.channel == CommunicationChannel.PUSH_NOTIFICATIONS }.enabled)
        assertFalse(saved.channels.single { it.channel == CommunicationChannel.TELEGRAM }.enabled)
    }

    @Test
    fun `should return failure when datasource throws`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val update = NotificationSettingsUpdate(id = Uuid.random(), appointmentEnabled = true, channels = emptyList())
        with(fixture) {
            coEvery { notificationSettingsDataSource.getByUserId(userId) } throws RuntimeException("db error")
        }

        whenn()
        val result = fixture.sut.invoke(userId, update)

        then()
        assertTrue(result.isFailure)
    }
}
