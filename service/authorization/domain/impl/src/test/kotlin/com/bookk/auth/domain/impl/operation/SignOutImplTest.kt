package com.bookk.auth.domain.impl.operation

import com.bookk.auth.domain.datasource.DeviceDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class SignOutImplTest {

    private class SutFixture {
        val deviceDataSource = mockk<DeviceDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val sut = SignOutImpl(deviceDataSource, transactionManager)
    }

    @Test
    fun `should sign out successfully by deleting device token`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val deviceId = Uuid.random()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { deviceDataSource.deleteTokenFromDevice(deviceId) } returns Unit
        }

        whenn()
        val result = fixture.sut.invoke(deviceId)

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { fixture.deviceDataSource.deleteTokenFromDevice(deviceId) }
    }

    @Test
    fun `should return failure when datasource throws`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val deviceId = Uuid.random()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { deviceDataSource.deleteTokenFromDevice(deviceId) } throws RuntimeException("db error")
        }

        whenn()
        val result = fixture.sut.invoke(deviceId)

        then()
        assertTrue(result.isFailure)
    }
}
