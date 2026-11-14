package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.datasource.AppointmentDataSource
import com.bookk.appointments.domain.datasource.AppointmentRequestDataSource
import com.bookk.core.domain.datasource.transaction.TransactionIsolation
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class EncryptPlaintextSnapshotsImplTest {

    private class SutFixture {
        val appointmentDataSource = mockk<AppointmentDataSource>()
        val appointmentRequestDataSource = mockk<AppointmentRequestDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val sut = EncryptPlaintextSnapshotsImpl(appointmentDataSource, appointmentRequestDataSource, transactionManager)
        val batchesInTransaction = mutableListOf<Boolean>()

        init {
            transactionManager.mockTransaction()
        }

        fun encryptAppointmentBatches(vararg sizes: Int) {
            val remaining = sizes.toMutableList()
            coEvery { appointmentDataSource.encryptPlaintextAppointments(any()) } coAnswers {
                batchesInTransaction += transactionManager.isInTransaction()
                remaining.removeFirst()
            }
        }

        fun encryptRequestBatches(vararg sizes: Int) {
            val remaining = sizes.toMutableList()
            coEvery { appointmentRequestDataSource.encryptPlaintextRequests(any()) } coAnswers {
                batchesInTransaction += transactionManager.isInTransaction()
                remaining.removeFirst()
            }
        }
    }

    @Test
    fun `should encrypt appointment batches until one comes back short`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.encryptAppointmentBatches(BATCH, BATCH, 3)
        fixture.encryptRequestBatches(0)

        whenn()
        val result = fixture.sut.invoke()

        then()
        assertTrue(result.isSuccess) { result.toString() }
        coVerify(exactly = 3) { fixture.appointmentDataSource.encryptPlaintextAppointments(BATCH) }
    }

    @Test
    fun `should encrypt request batches until one comes back short`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.encryptAppointmentBatches(0)
        fixture.encryptRequestBatches(BATCH, 7)

        whenn()
        val result = fixture.sut.invoke()

        then()
        assertTrue(result.isSuccess) { result.toString() }
        coVerify(exactly = 2) { fixture.appointmentRequestDataSource.encryptPlaintextRequests(BATCH) }
    }

    @Test
    fun `should encrypt every batch in a read committed transaction of its own`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.encryptAppointmentBatches(BATCH, 0)
        fixture.encryptRequestBatches(0)

        whenn()
        fixture.sut.invoke()

        then()
        assertEquals(listOf(true, true, true), fixture.batchesInTransaction)
        coVerify(exactly = 3) {
            fixture.transactionManager.transaction<Any>(TransactionIsolation.READ_COMMITTED, any())
        }
    }

    @Test
    fun `should fail without touching requests when an appointment batch fails`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val error = IllegalStateException("batch failed")
        coEvery { fixture.appointmentDataSource.encryptPlaintextAppointments(any()) } throws error

        whenn()
        val result = fixture.sut.invoke()

        then()
        assertSame(error, result.exceptionOrNull())
        coVerify(exactly = 0) { fixture.appointmentRequestDataSource.encryptPlaintextRequests(any()) }
    }

    private companion object {
        const val BATCH = 100
    }
}
