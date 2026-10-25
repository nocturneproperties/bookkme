package com.bookk.auth.domain.impl.operation.device

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
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class DeleteInactiveDevicesImplTest {

    private class SutFixture {
        val deviceDataSource = mockk<DeviceDataSource>()
        val eventProducer = mockk<StandardEventProducer>(relaxed = true)
        val transactionManager = mockk<TransactionManager>()
        val sut = DeleteInactiveDevicesImpl(deviceDataSource, eventProducer, transactionManager)
    }

    @Test
    fun `should delete inactive devices and emit DeviceDeleted event for each`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val deviceUuid1 = Uuid.random()
        val deviceUuid2 = Uuid.random()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { deviceDataSource.deleteInactiveDevices(any()) } returns listOf(deviceUuid1, deviceUuid2)
        }

        whenn()
        val result = fixture.sut.invoke()

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 2) { fixture.eventProducer.send(any<AuthEvent.DeviceDeleted>(), any()) }
    }

    @Test
    fun `should succeed and emit no events when no inactive devices exist`() = runUnitTest {
        given()
        val fixture = SutFixture()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { deviceDataSource.deleteInactiveDevices(any()) } returns emptyList()
        }

        whenn()
        val result = fixture.sut.invoke()

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 0) { fixture.eventProducer.send(any<AuthEvent.DeviceDeleted>(), any()) }
    }

    @Test
    fun `should return failure when datasource throws`() = runUnitTest {
        given()
        val fixture = SutFixture()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { deviceDataSource.deleteInactiveDevices(any()) } throws RuntimeException("db error")
        }

        whenn()
        val result = fixture.sut.invoke()

        then()
        assertTrue(result.isFailure)
    }
}
