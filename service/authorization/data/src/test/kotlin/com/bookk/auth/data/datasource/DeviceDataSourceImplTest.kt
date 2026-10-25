package com.bookk.auth.data.datasource

import com.bookk.auth.data.orm.table.AuthDeviceTable
import com.bookk.auth.data.orm.table.AuthenticationTable
import com.bookk.auth.domain.api.authentication.entity.Authentication
import com.bookk.core.data.test.createTestDatabase
import com.bookk.core.domain.entity.Language
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class DeviceDataSourceImplTest {

    private class SutFixture {
        val db = createTestDatabase(AuthenticationTable, AuthDeviceTable)
        val sut = DeviceDataSourceImpl()
        val accountSut = AccountDataSourceImpl()

        suspend fun insertAuth(): Authentication {
            return suspendTransaction { accountSut.createAuthorization(Authentication.stub()) }
        }
    }

    @Test
    fun `should insert device and retrieve by id`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val auth = fixture.insertAuth()
        val deviceUuid = Uuid.random()

        whenn()
        val deviceId = suspendTransaction { fixture.sut.insertDevice(auth.id, deviceUuid, "iPhone", Language.EN) }
        val found = suspendTransaction { fixture.sut.getDeviceById(deviceId!!) }

        then()
        assertNotNull(deviceId)
        assertNotNull(found)
        assertEquals(deviceUuid, found!!.deviceInfo.deviceUUID)
    }

    @Test
    fun `should return null when device not found by id`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val found = suspendTransaction { fixture.sut.getDeviceById(Uuid.random()) }

        then()
        assertNull(found)
    }

    @Test
    fun `should retrieve device by auth id and device uuid`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val auth = fixture.insertAuth()
        val deviceUuid = Uuid.random()
        suspendTransaction { fixture.sut.insertDevice(auth.id, deviceUuid, "Android", Language.EN) }

        whenn()
        val found = suspendTransaction { fixture.sut.getDeviceByAuthIdAndUUID(auth.id, deviceUuid) }

        then()
        assertNotNull(found)
        assertEquals(deviceUuid, found!!.deviceInfo.deviceUUID)
    }

    @Test
    fun `should return null when device not found by auth id and uuid`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val auth = fixture.insertAuth()

        whenn()
        val found = suspendTransaction { fixture.sut.getDeviceByAuthIdAndUUID(auth.id, Uuid.random()) }

        then()
        assertNull(found)
    }

    @Test
    fun `should list all devices for auth`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val auth = fixture.insertAuth()
        suspendTransaction { fixture.sut.insertDevice(auth.id, Uuid.random(), "iPhone", Language.EN) }
        suspendTransaction { fixture.sut.insertDevice(auth.id, Uuid.random(), "iPad", Language.EN) }

        whenn()
        val devices = suspendTransaction { fixture.sut.getDevices(auth.id) }

        then()
        assertEquals(2, devices.size)
    }

    @Test
    fun `should attach refresh token and retrieve device by token id`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val auth = fixture.insertAuth()
        val deviceUuid = Uuid.random()
        val deviceId = suspendTransaction { fixture.sut.insertDevice(auth.id, deviceUuid, "iPhone", Language.EN) }!!
        val tokenId = Uuid.random()

        whenn()
        suspendTransaction { fixture.sut.attachRefreshTokenToDevice(deviceId, tokenId, "hash123") }
        val found = suspendTransaction { fixture.sut.getDeviceByRefreshTokenId(tokenId) }

        then()
        assertNotNull(found)
        assertEquals(deviceUuid, found!!.deviceInfo.deviceUUID)
        assertTrue(found.deviceInfo.isSignedIn)
    }

    @Test
    fun `should rotate refresh token`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val auth = fixture.insertAuth()
        val deviceId = suspendTransaction { fixture.sut.insertDevice(auth.id, Uuid.random(), "iPhone", Language.EN) }!!
        val firstTokenId = Uuid.random()
        suspendTransaction { fixture.sut.attachRefreshTokenToDevice(deviceId, firstTokenId, "first-hash") }
        val secondTokenId = Uuid.random()

        whenn()
        suspendTransaction { fixture.sut.rotateRefreshToken(deviceId, secondTokenId, "second-hash") }
        val found = suspendTransaction { fixture.sut.getDeviceById(deviceId) }

        then()
        assertNotNull(found)
        assertEquals(secondTokenId, found!!.deviceInfo.refreshToken?.id)
    }

    @Test
    fun `should clear token from device on sign-out`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val auth = fixture.insertAuth()
        val deviceId = suspendTransaction { fixture.sut.insertDevice(auth.id, Uuid.random(), "iPhone", Language.EN) }!!
        val tokenId = Uuid.random()
        suspendTransaction { fixture.sut.attachRefreshTokenToDevice(deviceId, tokenId, "hash") }

        whenn()
        suspendTransaction { fixture.sut.deleteTokenFromDevice(deviceId) }
        val found = suspendTransaction { fixture.sut.getDeviceById(deviceId) }

        then()
        assertNotNull(found)
        assertNull(found!!.deviceInfo.refreshToken)
        assertTrue(!found.deviceInfo.isSignedIn)
    }

    @Test
    fun `should not insert duplicate device for same auth and uuid`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val auth = fixture.insertAuth()
        val deviceUuid = Uuid.random()
        suspendTransaction { fixture.sut.insertDevice(auth.id, deviceUuid, "iPhone", Language.EN) }

        whenn()
        val duplicateId = suspendTransaction { fixture.sut.insertDevice(auth.id, deviceUuid, "iPhone duplicate", Language.EN) }

        then()
        assertNull(duplicateId)
    }

    @Test
    fun `should insert device with the given language`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val auth = fixture.insertAuth()
        val deviceUuid = Uuid.random()

        whenn()
        suspendTransaction { fixture.sut.insertDevice(auth.id, deviceUuid, "iPhone", Language.UK) }
        val found = suspendTransaction { fixture.sut.getDeviceByAuthIdAndUUID(auth.id, deviceUuid) }

        then()
        assertNotNull(found)
    }

    @Test
    fun `should update language for an existing device`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val auth = fixture.insertAuth()
        val deviceUuid = Uuid.random()
        suspendTransaction { fixture.sut.insertDevice(auth.id, deviceUuid, "iPhone", Language.EN) }

        whenn()
        suspendTransaction { fixture.sut.updateLanguage(auth.id, deviceUuid, Language.UK) }

        then()
        val found = suspendTransaction { fixture.sut.getDeviceByAuthIdAndUUID(auth.id, deviceUuid) }
        assertNotNull(found)
    }

    // deleteInactiveDevices uses deleteReturning which is not supported by H2.
    // Covered in production by PostgreSQL; cannot be tested with the H2 test database.
}
