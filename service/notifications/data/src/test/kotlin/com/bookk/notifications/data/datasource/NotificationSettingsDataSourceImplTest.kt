package com.bookk.notifications.data.datasource

import com.bookk.core.data.test.createTestDatabase
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.notifications.data.orm.entity.NotificationChannelsEntity
import com.bookk.notifications.data.orm.entity.NotificationSettingsEntity
import com.bookk.notifications.data.orm.table.NotificationChannelsTable
import com.bookk.notifications.data.orm.table.NotificationSettingsTable
import com.bookk.notifications.domain.api.entity.CommunicationChannel
import com.bookk.notifications.domain.api.entity.NotificationChannelSettings
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class NotificationSettingsDataSourceImplTest {

    private class SutFixture {
        val db = createTestDatabase(NotificationSettingsTable, NotificationChannelsTable)
        val sut = NotificationSettingsDataSourceImpl()

        suspend fun insertSettings(userId: Uuid, appointmentEnabled: Boolean = true): Uuid {
            val settingsId = Uuid.random()
            suspendTransaction {
                NotificationSettingsEntity.new(settingsId) {
                    this.userId = userId
                    this.appointmentEnabled = appointmentEnabled
                }
            }
            return settingsId
        }

        suspend fun insertChannel(
            settingsId: Uuid,
            channel: CommunicationChannel,
            enabled: Boolean,
            availableToClients: Boolean = true,
        ): Uuid {
            val channelId = Uuid.random()
            suspendTransaction {
                NotificationChannelsEntity.new(channelId) {
                    this.settingsId = EntityID(settingsId, NotificationSettingsTable)
                    this.channel = channel
                    this.enabled = enabled
                    this.availableToClients = availableToClients
                }
            }
            return channelId
        }
    }

    @Test
    fun `should retrieve settings by user id`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val settingsId = fixture.insertSettings(userId, appointmentEnabled = true)
        fixture.insertChannel(settingsId, CommunicationChannel.EMAIL, enabled = true)
        fixture.insertChannel(settingsId, CommunicationChannel.PUSH_NOTIFICATIONS, enabled = false)

        whenn()
        val found = suspendTransaction { fixture.sut.getByUserId(userId) }

        then()
        assertNotNull(found)
        assertEquals(userId, found!!.userId)
        assertEquals(true, found.appointmentEnabled)
        assertEquals(2, found.channels.size)
    }

    @Test
    fun `should return null when settings not found for user`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val found = suspendTransaction { fixture.sut.getByUserId(Uuid.random()) }

        then()
        assertNull(found)
    }

    @Test
    fun `should retrieve settings without channels`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        fixture.insertSettings(userId, appointmentEnabled = false)

        whenn()
        val found = suspendTransaction { fixture.sut.getByUserId(userId) }

        then()
        assertNotNull(found)
        assertFalse(found!!.appointmentEnabled)
        assertEquals(0, found.channels.size)
    }

    @Test
    fun `should update channel enabled status`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val settingsId = fixture.insertSettings(userId)
        val channelId = fixture.insertChannel(settingsId, CommunicationChannel.EMAIL, enabled = true)

        whenn()
        suspendTransaction {
            fixture.sut.upsert(
                NotificationChannelSettings(channelId, CommunicationChannel.EMAIL, enabled = false, availableToClients = false)
            )
        }
        val found = suspendTransaction { fixture.sut.getByUserId(userId) }

        then()
        val channel = found!!.channels.first { it.channel == CommunicationChannel.EMAIL }
        assertFalse(channel.enabled)
        assertFalse(channel.availableToClients)
    }

    @Test
    fun `should delete settings and cascade channels for the user`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val settingsId = fixture.insertSettings(userId)
        fixture.insertChannel(settingsId, CommunicationChannel.EMAIL, enabled = true)

        whenn()
        suspendTransaction { fixture.sut.deleteByUserId(userId) }

        then()
        assertNull(suspendTransaction { fixture.sut.getByUserId(userId) })
    }

    @Test
    fun `should not fail deleting settings for a user with none`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val result = runCatching { suspendTransaction { fixture.sut.deleteByUserId(Uuid.random()) } }

        then()
        assertTrue(result.isSuccess)
    }

    // upsert(NotificationSettings) uses upsertReturning which is PostgreSQL-only and not
    // supported by H2. Covered in production; cannot be tested with the H2 test database.
}
