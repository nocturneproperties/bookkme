package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.datasource.AppointmentDataSource
import com.bookk.appointments.domain.datasource.AppointmentRequestDataSource
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

internal class MergeClientTest {

    private class SutFixture {
        val appointmentDataSource = mockk<AppointmentDataSource>()
        val appointmentRequestDataSource = mockk<AppointmentRequestDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val sut = MergeClient(appointmentDataSource, appointmentRequestDataSource, transactionManager)
    }

    @Test
    fun `should move appointments and requests of the merged client to the remaining one`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val businessId = Uuid.random()
        val fromClientId = Uuid.random()
        val toClientId = Uuid.random()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { appointmentDataSource.reassignClient(businessId, fromClientId, toClientId) } returns Unit
            coEvery { appointmentRequestDataSource.reassignClient(businessId, fromClientId, toClientId) } returns Unit
        }

        whenn()
        val result = fixture.sut(businessId, fromClientId, toClientId)

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { fixture.appointmentDataSource.reassignClient(businessId, fromClientId, toClientId) }
        coVerify(exactly = 1) { fixture.appointmentRequestDataSource.reassignClient(businessId, fromClientId, toClientId) }
    }

    @Test
    fun `should fail so the event is retried when a write fails`() = runUnitTest {
        given()
        val fixture = SutFixture()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { appointmentDataSource.reassignClient(any(), any(), any()) } throws IllegalStateException("database down")
        }

        whenn()
        val result = fixture.sut(Uuid.random(), Uuid.random(), Uuid.random())

        then()
        assertTrue(result.isFailure)
    }
}
