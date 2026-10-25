package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.datasource.AppointmentDataSource
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
import kotlin.time.Instant

internal class MarkAppointmentsCompletedImplTest {

    private class SutFixture {
        val appointmentDataSource = mockk<AppointmentDataSource>()
        val transactionManager = mockk<TransactionManager>()

        val sut = MarkAppointmentsCompletedImpl(
            appointmentDataSource,
            transactionManager
        )
    }

    @Test
    fun `should mark overdue appointments as completed`() = runUnitTest {
        given()
        val fixture = SutFixture()

        with(fixture) {
            coEvery { appointmentDataSource.markCompleted(any<Instant>()) } returns Unit
            transactionManager.mockTransaction()
        }

        whenn()
        val result = fixture.sut.invoke()

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { fixture.appointmentDataSource.markCompleted(any()) }
    }

    @Test
    fun `should return failure when datasource fails`() = runUnitTest {
        given()
        val fixture = SutFixture()

        with(fixture) {
            coEvery { appointmentDataSource.markCompleted(any<Instant>()) } throws RuntimeException("DB error")
            transactionManager.mockTransaction()
        }

        whenn()
        val result = fixture.sut.invoke()

        then()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message == "DB error")
    }
}
