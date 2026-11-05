package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.api.entity.Appointment
import com.bookk.appointments.domain.datasource.AppointmentDataSource
import com.bookk.core.domain.datasource.transaction.TransactionIsolation
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.Instant

internal class MarkAppointmentsCompletedImplTest {

    private class SutFixture {
        val appointmentDataSource = mockk<AppointmentDataSource>()
        val transactionManager = mockk<TransactionManager>()

        val sut = MarkAppointmentsCompletedImpl(
            appointmentDataSource,
            transactionManager,
        )
    }

    @Test
    fun `should expire the edit window before completing appointments at the end of their local day`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val cutoff = slot<Instant>()
        val completionTime = slot<Instant>()

        with(fixture) {
            coEvery { appointmentDataSource.markUnresolved(capture(cutoff)) } returns Unit
            coEvery { appointmentDataSource.markCompleted(capture(completionTime)) } returns Unit
            transactionManager.mockTransaction()
        }
        val startedAt = Clock.System.now()

        whenn()
        val result = fixture.sut.invoke()
        val finishedAt = Clock.System.now()

        then()
        assertTrue(result.isSuccess)
        assertTrue(cutoff.captured in (startedAt - Appointment.EDIT_WINDOW)..(finishedAt - Appointment.EDIT_WINDOW))
        assertTrue(completionTime.captured in startedAt..finishedAt)
        assertTrue(cutoff.captured == completionTime.captured - Appointment.EDIT_WINDOW)
        coVerifyOrder {
            fixture.appointmentDataSource.markUnresolved(any())
            fixture.appointmentDataSource.markCompleted(any())
        }
        coVerify(exactly = 1) { fixture.transactionManager.transaction<Any>(TransactionIsolation.READ_COMMITTED, any()) }
    }

    @Test
    fun `should return failure when datasource fails`() = runUnitTest {
        given()
        val fixture = SutFixture()

        with(fixture) {
            coEvery { appointmentDataSource.markUnresolved(any()) } returns Unit
            coEvery { appointmentDataSource.markCompleted(any<Instant>()) } throws RuntimeException("DB error")
            transactionManager.mockTransaction()
        }

        whenn()
        val result = fixture.sut.invoke()

        then()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message == "DB error")
    }

    @Test
    fun `should not complete appointments when expiration fails`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.transactionManager.mockTransaction()
        coEvery { fixture.appointmentDataSource.markUnresolved(any()) } throws IllegalStateException("Expiration failed")

        whenn()
        val result = fixture.sut()

        then()
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        coVerify(exactly = 0) { fixture.appointmentDataSource.markCompleted(any()) }
    }
}
