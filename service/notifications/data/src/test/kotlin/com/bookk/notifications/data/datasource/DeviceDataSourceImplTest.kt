package com.bookk.notifications.data.datasource

import com.bookk.core.data.test.createTestDatabase
import com.bookk.core.domain.entity.Error
import com.bookk.core.domain.entity.Language
import com.bookk.core.domain.entity.runSuspendCatching
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.notifications.data.orm.table.DeviceTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.update
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class DeviceDataSourceImplTest {

    private class SutFixture {
        val db = createTestDatabase(DeviceTable)
        val sut = DeviceDataSourceImpl()
    }

    @Test
    fun `should create a device without a push token`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val authId = Uuid.random()
        val deviceUuid = Uuid.random()
        val userId = Uuid.random()

        whenn()
        val created = suspendTransaction { fixture.sut.getOrCreate(authId, deviceUuid, userId, Language.EN) }

        then()
        assertEquals(listOf(authId, deviceUuid, userId), listOf(created.authId, created.deviceUuid, created.userId))
        assertNull(created.notificationToken)
        assertEquals(Language.EN, created.language)
        assertEquals(listOf(created), suspendTransaction { fixture.sut.getByUserId(userId) })
    }

    @Test
    fun `should retrieve all devices for user`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        suspendTransaction { fixture.sut.getOrCreate(Uuid.random(), Uuid.random(), userId, Language.EN) }
        suspendTransaction { fixture.sut.getOrCreate(Uuid.random(), Uuid.random(), userId, Language.UK) }

        whenn()
        val devices = suspendTransaction { fixture.sut.getByUserId(userId) }

        then()
        assertEquals(2, devices.size)
        assertTrue(devices.all { it.userId == userId })
    }

    @Test
    fun `should return empty list when no devices for user`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val devices = suspendTransaction { fixture.sut.getByUserId(Uuid.random()) }

        then()
        assertTrue(devices.isEmpty())
    }

    @Test
    fun `should update notification token`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val deviceUuid = Uuid.random()
        val authId = Uuid.random()
        suspendTransaction { fixture.sut.getOrCreate(authId, deviceUuid, Uuid.random(), Language.EN) }

        whenn()
        val updated = suspendTransaction { fixture.sut.updateToken(authId, deviceUuid, "fcm-token-123") }

        then()
        assertEquals("fcm-token-123", updated.notificationToken)
    }

    @Test
    fun `should clear notification token`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val deviceUuid = Uuid.random()
        val authId = Uuid.random()
        suspendTransaction { fixture.sut.getOrCreate(authId, deviceUuid, Uuid.random(), Language.EN) }
        suspendTransaction { fixture.sut.updateToken(authId, deviceUuid, "fcm-token-123") }

        whenn()
        val updated = suspendTransaction { fixture.sut.updateToken(authId, deviceUuid, null) }

        then()
        assertNull(updated.notificationToken)
    }

    @Test
    fun `should update language for an existing device`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val deviceUuid = Uuid.random()
        val authId = Uuid.random()
        suspendTransaction { fixture.sut.getOrCreate(authId, deviceUuid, Uuid.random(), Language.EN) }

        whenn()
        val updated = suspendTransaction { fixture.sut.updateLanguage(authId, deviceUuid, Language.UK) }

        then()
        assertEquals(Language.UK, updated.language)
    }

    @Test
    fun `should delete an account's device`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val deviceUuid = Uuid.random()
        val authId = Uuid.random()
        val userId = Uuid.random()
        suspendTransaction { fixture.sut.getOrCreate(authId, deviceUuid, userId, Language.EN) }

        whenn()
        suspendTransaction { fixture.sut.delete(authId, deviceUuid) }

        then()
        assertTrue(suspendTransaction { fixture.sut.getByUserId(userId) }.isEmpty())
    }

    @Test
    fun `should preserve the device token language and id when creation is replayed`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val authId = Uuid.random()
        val deviceUuid = Uuid.random()
        val userId = Uuid.random()
        val created = suspendTransaction { fixture.sut.getOrCreate(authId, deviceUuid, userId, Language.EN) }
        suspendTransaction {
            fixture.sut.updateToken(authId, deviceUuid, "current-token")
            fixture.sut.updateLanguage(authId, deviceUuid, Language.UK)
        }

        whenn()
        val replayed = suspendTransaction { fixture.sut.getOrCreate(authId, deviceUuid, userId, Language.EN) }

        then()
        assertEquals(created.id, replayed.id)
        assertEquals("current-token", replayed.notificationToken)
        assertEquals(Language.UK, replayed.language)
        assertEquals(listOf(replayed), suspendTransaction { fixture.sut.getByUserId(userId) })
    }

    @Test
    fun `should keep separate account associations for the same device uuid`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val deviceUuid = Uuid.random()
        val first = suspendTransaction { fixture.sut.getOrCreate(Uuid.random(), deviceUuid, Uuid.random(), Language.EN) }

        whenn()
        val second = suspendTransaction { fixture.sut.getOrCreate(Uuid.random(), deviceUuid, Uuid.random(), Language.UK) }

        then()
        assertTrue(first.id != second.id)
        assertEquals(listOf(first), suspendTransaction { fixture.sut.getByUserId(first.userId) })
        assertEquals(listOf(second), suspendTransaction { fixture.sut.getByUserId(second.userId) })
    }

    @Test
    fun `should update and delete only the requested account on a shared device`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val deviceUuid = Uuid.random()
        val first = suspendTransaction { fixture.sut.getOrCreate(Uuid.random(), deviceUuid, Uuid.random(), Language.EN) }
        val second = suspendTransaction { fixture.sut.getOrCreate(Uuid.random(), deviceUuid, Uuid.random(), Language.EN) }

        whenn()
        suspendTransaction {
            fixture.sut.updateToken(first.authId, deviceUuid, "first-token")
            fixture.sut.updateToken(second.authId, deviceUuid, "second-token")
            fixture.sut.updateLanguage(first.authId, deviceUuid, Language.UK)
        }
        val updatedFirst = suspendTransaction { fixture.sut.getByUserId(first.userId) }.single()
        suspendTransaction { fixture.sut.delete(first.authId, deviceUuid) }
        suspendTransaction { fixture.sut.delete(first.authId, deviceUuid) }

        then()
        assertEquals("first-token", updatedFirst.notificationToken)
        assertEquals(Language.UK, updatedFirst.language)
        assertTrue(suspendTransaction { fixture.sut.getByUserId(first.userId) }.isEmpty())
        assertEquals(listOf(second.copy(notificationToken = "second-token")), suspendTransaction { fixture.sut.getByUserId(second.userId) })
    }

    @Test
    fun `should refuse token updates for another account's device`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val owner = suspendTransaction { fixture.sut.getOrCreate(Uuid.random(), Uuid.random(), Uuid.random(), Language.EN) }

        whenn()
        val result = runSuspendCatching {
            suspendTransaction { fixture.sut.updateToken(Uuid.random(), owner.deviceUuid, "intruder-token") }
        }

        then()
        assertTrue(result.exceptionOrNull() is Error.NotFound)
        assertEquals(listOf(owner), suspendTransaction { fixture.sut.getByUserId(owner.userId) })
    }

    @Test
    fun `should refuse a replay that changes the user of an existing account device`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val owner = suspendTransaction { fixture.sut.getOrCreate(Uuid.random(), Uuid.random(), Uuid.random(), Language.EN) }

        whenn()
        val result = runSuspendCatching {
            suspendTransaction { fixture.sut.getOrCreate(owner.authId, owner.deviceUuid, Uuid.random(), Language.UK) }
        }

        then()
        assertTrue(result.exceptionOrNull() is Error.OperationNotAllowed)
        assertEquals(listOf(owner), suspendTransaction { fixture.sut.getByUserId(owner.userId) })
    }

    @Test
    fun `should return the language of the newest device`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        suspendTransaction {
            val older = fixture.sut.getOrCreate(Uuid.random(), Uuid.random(), userId, Language.EN)
            val newer = fixture.sut.getOrCreate(Uuid.random(), Uuid.random(), userId, Language.PL)
            DeviceTable.update({ DeviceTable.id eq older.id }) { it[createdAt] = Instant.fromEpochSeconds(1_000) }
            DeviceTable.update({ DeviceTable.id eq newer.id }) { it[createdAt] = Instant.fromEpochSeconds(2_000) }
        }

        whenn()
        val language = suspendTransaction { fixture.sut.getLastUsedLanguage(userId) }

        then()
        assertEquals(Language.PL, language)
    }

    @Test
    fun `should return the language of the device signed in to most recently`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val older = suspendTransaction {
            val older = fixture.sut.getOrCreate(Uuid.random(), Uuid.random(), userId, Language.EN)
            val newer = fixture.sut.getOrCreate(Uuid.random(), Uuid.random(), userId, Language.PL)
            DeviceTable.update({ DeviceTable.id eq older.id }) { it[createdAt] = Instant.fromEpochSeconds(1_000) }
            DeviceTable.update({ DeviceTable.id eq newer.id }) { it[createdAt] = Instant.fromEpochSeconds(2_000) }
            older
        }
        suspendTransaction { fixture.sut.updateLanguage(older.authId, older.deviceUuid, Language.UK) }

        whenn()
        val language = suspendTransaction { fixture.sut.getLastUsedLanguage(userId) }

        then()
        assertEquals(Language.UK, language)
    }

    @Test
    fun `should return no language for a user without devices`() = runUnitTest {
        given()
        val fixture = SutFixture()
        suspendTransaction { fixture.sut.getOrCreate(Uuid.random(), Uuid.random(), Uuid.random(), Language.PL) }

        whenn()
        val language = suspendTransaction { fixture.sut.getLastUsedLanguage(Uuid.random()) }

        then()
        assertNull(language)
    }
}
