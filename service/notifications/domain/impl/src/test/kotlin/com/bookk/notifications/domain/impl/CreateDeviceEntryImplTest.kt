package com.bookk.notifications.domain.impl

import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.domain.entity.Language
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

internal class CreateDeviceEntryImplTest {

    private class SutFixture {
        val deviceDataSource = mockk<DeviceDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val sut = CreateDeviceEntryImpl(deviceDataSource, transactionManager)
    }

    @Test
    fun `should create device entry and return created device`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val deviceId = Uuid.random()
        val authId = Uuid.random()
        val userId = Uuid.random()
        val device = Device.stub(authId = authId, deviceId = deviceId, userId = userId, language = Language.UK)
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { deviceDataSource.create(authId, deviceId, userId, Language.UK) } returns device
        }

        whenn()
        val result = fixture.sut.invoke(deviceId, authId, userId, Language.UK)

        then()
        assertTrue(result.isSuccess)
        assertEquals(device, result.getOrNull())
        coVerify(exactly = 1) { fixture.deviceDataSource.create(authId, deviceId, userId, Language.UK) }
    }

    @Test
    fun `should return failure when datasource throws`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val deviceId = Uuid.random()
        val authId = Uuid.random()
        val userId = Uuid.random()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { deviceDataSource.create(authId, deviceId, userId, Language.EN) } throws RuntimeException("db error")
        }

        whenn()
        val result = fixture.sut.invoke(deviceId, authId, userId, Language.EN)

        then()
        assertTrue(result.isFailure)
    }
}
