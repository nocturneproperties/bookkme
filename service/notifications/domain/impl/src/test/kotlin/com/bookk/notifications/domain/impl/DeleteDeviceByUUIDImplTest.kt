package com.bookk.notifications.domain.impl

import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.notifications.domain.datasource.DeviceDataSource
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class DeleteDeviceByUUIDImplTest {

    private class SutFixture {
        val deviceDataSource = mockk<DeviceDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val sut = DeleteDeviceByUUIDImpl(deviceDataSource, transactionManager)
    }

    @Test
    fun `should delete device by uuid successfully`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val deviceUUID = Uuid.random()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { deviceDataSource.deleteByDeviceUuid(deviceUUID) } just Runs
        }

        whenn()
        val result = fixture.sut.invoke(deviceUUID)

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { fixture.deviceDataSource.deleteByDeviceUuid(deviceUUID) }
    }

    @Test
    fun `should return failure when datasource throws`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val deviceUUID = Uuid.random()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { deviceDataSource.deleteByDeviceUuid(deviceUUID) } throws RuntimeException("db error")
        }

        whenn()
        val result = fixture.sut.invoke(deviceUUID)

        then()
        assertTrue(result.isFailure)
    }
}
