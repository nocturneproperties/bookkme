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
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class DeviceDataSourceImplTest {

    private class SutFixture {
        val db = createTestDatabase(DeviceTable)
        val sut = DeviceDataSourceImpl()
    }

    @Test
    fun `should create device and retrieve by id`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val authId = Uuid.random()
        val deviceUuid = Uuid.random()
        val userId = Uuid.random()

        whenn()
        val created = suspendTransaction { fixture.sut.create(authId, deviceUuid, userId, Language.EN) }
        val found = suspendTransaction { fixture.sut.getById(created.id) }

        then()
        assertNotNull(found)
        assertEquals(authId, found!!.authId)
        assertEquals(deviceUuid, found.deviceUuid)
        assertEquals(userId, found.userId)
        assertNull(found.notificationToken)
        assertEquals(Language.EN, found.language)
    }

    @Test
    fun `should return null when device not found by id`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val found = suspendTransaction { fixture.sut.getById(Uuid.random()) }

        then()
        assertNull(found)
    }

    @Test
    fun `should retrieve device by device uuid`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val deviceUuid = Uuid.random()
        val authId = Uuid.random()
        val created = suspendTransaction { fixture.sut.create(authId, deviceUuid, Uuid.random(), Language.EN) }

        whenn()
        val found = suspendTransaction { fixture.sut.getByAuthIdAndDeviceUuid(authId, deviceUuid) }

        then()
        assertNotNull(found)
        assertEquals(created.id, found!!.id)
    }

    @Test
    fun `should return null when device uuid not found`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val found = suspendTransaction { fixture.sut.getByAuthIdAndDeviceUuid(Uuid.random(), Uuid.random()) }

        then()
        assertNull(found)
    }

    @Test
    fun `should retrieve device by auth id`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val authId = Uuid.random()
        val created = suspendTransaction { fixture.sut.create(authId, Uuid.random(), Uuid.random(), Language.EN) }

        whenn()
        val found = suspendTransaction { fixture.sut.getByAuthId(authId) }

        then()
        assertNotNull(found)
        assertEquals(created.id, found!!.id)
    }

    @Test
    fun `should return null when auth id not found`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val found = suspendTransaction { fixture.sut.getByAuthId(Uuid.random()) }

        then()
        assertNull(found)
    }

    @Test
    fun `should retrieve all devices for user`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        suspendTransaction { fixture.sut.create(Uuid.random(), Uuid.random(), userId, Language.EN) }
        suspendTransaction { fixture.sut.create(Uuid.random(), Uuid.random(), userId, Language.UK) }

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
        suspendTransaction { fixture.sut.create(authId, deviceUuid, Uuid.random(), Language.EN) }

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
        suspendTransaction { fixture.sut.create(authId, deviceUuid, Uuid.random(), Language.EN) }
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
        suspendTransaction { fixture.sut.create(authId, deviceUuid, Uuid.random(), Language.EN) }

        whenn()
        val updated = suspendTransaction { fixture.sut.updateLanguage(authId, deviceUuid, Language.UK) }

        then()
        assertEquals(Language.UK, updated.language)
    }

    @Test
    fun `should delete device by device uuid`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val deviceUuid = Uuid.random()
        val authId = Uuid.random()
        suspendTransaction { fixture.sut.create(authId, deviceUuid, Uuid.random(), Language.EN) }

        whenn()
        suspendTransaction { fixture.sut.deleteByDeviceUuid(authId, deviceUuid) }

        then()
        val found = suspendTransaction { fixture.sut.getByAuthIdAndDeviceUuid(authId, deviceUuid) }
        assertNull(found)
    }

    @Test
    fun `should preserve the device token language and id when creation is replayed`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val authId = Uuid.random()
        val deviceUuid = Uuid.random()
        val userId = Uuid.random()
        val created = suspendTransaction { fixture.sut.create(authId, deviceUuid, userId, Language.EN) }
        suspendTransaction {
            fixture.sut.updateToken(authId, deviceUuid, "current-token")
            fixture.sut.updateLanguage(authId, deviceUuid, Language.UK)
        }

        whenn()
        val replayed = suspendTransaction { fixture.sut.create(authId, deviceUuid, userId, Language.EN) }

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
        val first = suspendTransaction { fixture.sut.create(Uuid.random(), deviceUuid, Uuid.random(), Language.EN) }

        whenn()
        val second = suspendTransaction { fixture.sut.create(Uuid.random(), deviceUuid, Uuid.random(), Language.UK) }

        then()
        assertTrue(first.id != second.id)
        assertEquals(first, suspendTransaction { fixture.sut.getByAuthIdAndDeviceUuid(first.authId, deviceUuid) })
        assertEquals(second, suspendTransaction { fixture.sut.getByAuthIdAndDeviceUuid(second.authId, deviceUuid) })
    }

    @Test
    fun `should update and delete only the requested account on a shared device`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val deviceUuid = Uuid.random()
        val first = suspendTransaction { fixture.sut.create(Uuid.random(), deviceUuid, Uuid.random(), Language.EN) }
        val second = suspendTransaction { fixture.sut.create(Uuid.random(), deviceUuid, Uuid.random(), Language.EN) }

        whenn()
        suspendTransaction {
            fixture.sut.updateToken(first.authId, deviceUuid, "first-token")
            fixture.sut.updateToken(second.authId, deviceUuid, "second-token")
            fixture.sut.updateLanguage(first.authId, deviceUuid, Language.UK)
        }
        val updatedFirst = suspendTransaction { fixture.sut.getByAuthIdAndDeviceUuid(first.authId, deviceUuid) }
        val updatedSecond = suspendTransaction { fixture.sut.getByAuthIdAndDeviceUuid(second.authId, deviceUuid) }
        suspendTransaction { fixture.sut.deleteByDeviceUuid(first.authId, deviceUuid) }
        suspendTransaction { fixture.sut.deleteByDeviceUuid(first.authId, deviceUuid) }

        then()
        assertEquals("first-token", updatedFirst?.notificationToken)
        assertEquals(Language.UK, updatedFirst?.language)
        assertEquals(second.copy(notificationToken = "second-token"), updatedSecond)
        assertNull(suspendTransaction { fixture.sut.getByAuthIdAndDeviceUuid(first.authId, deviceUuid) })
        assertEquals(updatedSecond, suspendTransaction { fixture.sut.getByAuthIdAndDeviceUuid(second.authId, deviceUuid) })
    }

    @Test
    fun `should refuse token updates for another account's device`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val owner = suspendTransaction { fixture.sut.create(Uuid.random(), Uuid.random(), Uuid.random(), Language.EN) }

        whenn()
        val result = runSuspendCatching {
            suspendTransaction { fixture.sut.updateToken(Uuid.random(), owner.deviceUuid, "intruder-token") }
        }

        then()
        assertTrue(result.exceptionOrNull() is Error.NotFound)
        assertEquals(owner, suspendTransaction { fixture.sut.getById(owner.id) })
    }

    @Test
    fun `should refuse a replay that changes the user of an existing account device`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val owner = suspendTransaction { fixture.sut.create(Uuid.random(), Uuid.random(), Uuid.random(), Language.EN) }

        whenn()
        val result = runSuspendCatching {
            suspendTransaction { fixture.sut.create(owner.authId, owner.deviceUuid, Uuid.random(), Language.UK) }
        }

        then()
        assertTrue(result.exceptionOrNull() is Error.OperationNotAllowed)
        assertEquals(owner, suspendTransaction { fixture.sut.getById(owner.id) })
    }
}
