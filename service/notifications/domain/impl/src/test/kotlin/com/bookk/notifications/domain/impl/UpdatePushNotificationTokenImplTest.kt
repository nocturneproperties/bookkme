package com.bookk.notifications.domain.impl

import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.notifications.domain.api.entity.Device
import com.bookk.notifications.domain.datasource.DeviceDataSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class UpdatePushNotificationTokenImplTest {

    private class SutFixture {
        val deviceDataSource = mockk<DeviceDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val sut = UpdatePushNotificationTokenImpl(deviceDataSource, transactionManager)
    }

    @Test
    fun `should update push notification token and return updated device`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val deviceId = Uuid.random()
        val token = "fcm-token-abc123"
        val updatedDevice = Device.stub(deviceId = deviceId, notificationToken = token)
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { deviceDataSource.updateToken(deviceId, token) } returns updatedDevice
        }

        whenn()
        val result = fixture.sut.invoke(deviceId, token)

        then()
        assertTrue(result.isSuccess)
        assertEquals(updatedDevice, result.getOrNull())
        coVerify(exactly = 1) { fixture.deviceDataSource.updateToken(deviceId, token) }
    }

    @Test
    fun `should return failure when datasource throws`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val deviceId = Uuid.random()
        val token = "fcm-token-abc123"
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { deviceDataSource.updateToken(deviceId, token) } throws RuntimeException("device not found")
        }

        whenn()
        val result = fixture.sut.invoke(deviceId, token)

        then()
        assertTrue(result.isFailure)
    }
}
