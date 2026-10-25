package com.bookk.appointments.domain.impl.operation

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

internal class DeleteOutdatedRequestsImplTest {

    private class SutFixture {
        val appointmentRequestDataSource = mockk<AppointmentRequestDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val sut = DeleteOutdatedRequestsImpl(appointmentRequestDataSource, transactionManager)
    }

    @Test
    fun `should cancel outdated pending requests`() = runUnitTest {
        given()
        val fixture = SutFixture()
        with(fixture) {
            coEvery { appointmentRequestDataSource.cancelOutdated(any()) } returns Unit
            transactionManager.mockTransaction()
        }

        whenn()
        val result = fixture.sut.invoke()

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { fixture.appointmentRequestDataSource.cancelOutdated(any()) }
    }

    @Test
    fun `should return failure when datasource fails`() = runUnitTest {
        given()
        val fixture = SutFixture()
        with(fixture) {
            coEvery { appointmentRequestDataSource.cancelOutdated(any()) } throws RuntimeException("DB error")
            transactionManager.mockTransaction()
        }

        whenn()
        val result = fixture.sut.invoke()

        then()
        assertTrue(result.isFailure)
    }
}
