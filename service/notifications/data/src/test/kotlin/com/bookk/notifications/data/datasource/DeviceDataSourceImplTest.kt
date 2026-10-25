package com.bookk.notifications.data.datasource

import com.bookk.core.data.test.createTestDatabase
import com.bookk.core.domain.entity.Language
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
        val created = suspendTransaction { fixture.sut.create(Uuid.random(), deviceUuid, Uuid.random(), Language.EN) }

        whenn()
        val found = suspendTransaction { fixture.sut.getByDeviceUuid(deviceUuid) }

        then()
        assertNotNull(found)
        assertEquals(created.id, found!!.id)
    }

    @Test
    fun `should return null when device uuid not found`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val found = suspendTransaction { fixture.sut.getByDeviceUuid(Uuid.random()) }

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
        suspendTransaction { fixture.sut.create(Uuid.random(), deviceUuid, Uuid.random(), Language.EN) }

        whenn()
        val updated = suspendTransaction { fixture.sut.updateToken(deviceUuid, "fcm-token-123") }

        then()
        assertEquals("fcm-token-123", updated.notificationToken)
    }

    @Test
    fun `should clear notification token`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val deviceUuid = Uuid.random()
        suspendTransaction { fixture.sut.create(Uuid.random(), deviceUuid, Uuid.random(), Language.EN) }
        suspendTransaction { fixture.sut.updateToken(deviceUuid, "fcm-token-123") }

        whenn()
        val updated = suspendTransaction { fixture.sut.updateToken(deviceUuid, null) }

        then()
        assertNull(updated.notificationToken)
    }

    @Test
    fun `should update language for an existing device`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val deviceUuid = Uuid.random()
        suspendTransaction { fixture.sut.create(Uuid.random(), deviceUuid, Uuid.random(), Language.EN) }

        whenn()
        val updated = suspendTransaction { fixture.sut.updateLanguage(deviceUuid, Language.UK) }

        then()
        assertEquals(Language.UK, updated.language)
    }

    @Test
    fun `should delete device by device uuid`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val deviceUuid = Uuid.random()
        suspendTransaction { fixture.sut.create(Uuid.random(), deviceUuid, Uuid.random(), Language.EN) }

        whenn()
        suspendTransaction { fixture.sut.deleteByDeviceUuid(deviceUuid) }

        then()
        val found = suspendTransaction { fixture.sut.getByDeviceUuid(deviceUuid) }
        assertNull(found)
    }
}
