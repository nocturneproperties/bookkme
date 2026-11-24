package com.bookk.auth.domain.impl.operation

import com.bookk.auth.domain.api.identification.entity.Device
import com.bookk.auth.domain.datasource.DeviceDataSource
import com.bookk.core.data.eventstreaming.StandardEventProducer
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.server.auth.client.AuthEvent
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.uuid.Uuid

internal class SignOutImplTest {

    private class SutFixture {
        val deviceDataSource = mockk<DeviceDataSource>()
        val eventProducer = mockk<StandardEventProducer>(relaxed = true)
        val transactionManager = mockk<TransactionManager>()
        val sut = SignOutImpl(deviceDataSource, DeviceSignOut(deviceDataSource, eventProducer), transactionManager)
    }

    @Test
    fun `should lock the device before clearing its refresh token`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val device = Device.stub()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { deviceDataSource.getDeviceByIdForUpdate(device.deviceInfo.id) } returns device
            coEvery { deviceDataSource.deleteTokenFromDevice(device.deviceInfo.id) } returns Unit
        }

        whenn()
        val result = fixture.sut.invoke(device.deviceInfo.id)

        then()
        assertTrue(result.isSuccess)
        coVerifyOrder {
            fixture.deviceDataSource.getDeviceByIdForUpdate(device.deviceInfo.id)
            fixture.deviceDataSource.deleteTokenFromDevice(device.deviceInfo.id)
        }
    }

    @Test
    fun `should publish DeviceSignedOut for the signed out device`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val device = Device.stub()
        val before = Clock.System.now()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { deviceDataSource.getDeviceByIdForUpdate(device.deviceInfo.id) } returns device
            coEvery { deviceDataSource.deleteTokenFromDevice(device.deviceInfo.id) } returns Unit
        }

        whenn()
        fixture.sut.invoke(device.deviceInfo.id)

        then()
        coVerify(exactly = 1) {
            fixture.eventProducer.send(
                match<AuthEvent.DeviceSignedOut> {
                    val isSameDevice = it.deviceUuid == device.deviceInfo.deviceUUID
                    isSameDevice && it.authId == device.authRecord.id && it.signedOutAt >= before
                },
                any(),
            )
        }
    }

    @Test
    fun `should succeed without publishing when the device no longer exists`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val deviceId = Uuid.random()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { deviceDataSource.getDeviceByIdForUpdate(deviceId) } returns null
        }

        whenn()
        val result = fixture.sut.invoke(deviceId)

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { fixture.deviceDataSource.deleteTokenFromDevice(any()) }
        coVerify(exactly = 0) { fixture.eventProducer.send(any<AuthEvent.DeviceSignedOut>(), any()) }
    }

    @Test
    fun `should return failure when datasource throws`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val device = Device.stub()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { deviceDataSource.getDeviceByIdForUpdate(device.deviceInfo.id) } returns device
            coEvery { deviceDataSource.deleteTokenFromDevice(device.deviceInfo.id) } throws RuntimeException("db error")
        }

        whenn()
        val result = fixture.sut.invoke(device.deviceInfo.id)

        then()
        assertTrue(result.isFailure)
    }
}
