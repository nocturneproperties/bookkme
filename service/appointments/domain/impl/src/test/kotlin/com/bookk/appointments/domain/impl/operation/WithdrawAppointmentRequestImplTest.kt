package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.api.entity.AppointmentRequest
import com.bookk.appointments.domain.api.entity.AppointmentRequestStatus
import com.bookk.appointments.domain.api.entity.AppointmentRequestStatusError
import com.bookk.appointments.domain.datasource.AppointmentRequestDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.domain.entity.Error
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class WithdrawAppointmentRequestImplTest {

    private class SutFixture {
        val requestDataSource = mockk<AppointmentRequestDataSource> { coEvery { withdraw(any()) } returns Unit }
        val transactionManager = mockk<TransactionManager>()
        val sut = WithdrawAppointmentRequestImpl(requestDataSource, transactionManager)
    }

    @Test
    fun `should withdraw the caller's pending request`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val request = AppointmentRequest.stub(initiatorUserId = userId)
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { requestDataSource.getForUpdate(request.id) } returns request
        }

        whenn()
        val result = fixture.sut(userId, request.id)

        then()
        assertTrue(result.isSuccess)
        coVerifyOrder {
            fixture.requestDataSource.getForUpdate(request.id)
            fixture.requestDataSource.withdraw(request.id)
        }
    }

    @Test
    fun `should return not found when the request does not exist`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val requestId = Uuid.random()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { requestDataSource.getForUpdate(requestId) } returns null
        }

        whenn()
        val result = fixture.sut(Uuid.random(), requestId)

        then()
        assertTrue(result.exceptionOrNull() is Error.NotFound)
    }

    @Test
    fun `should return not found when someone else made the request`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val request = AppointmentRequest.stub()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { requestDataSource.getForUpdate(request.id) } returns request
        }

        whenn()
        val result = fixture.sut(Uuid.random(), request.id)

        then()
        assertTrue(result.exceptionOrNull() is Error.NotFound)
        coVerify(exactly = 0) { fixture.requestDataSource.withdraw(any()) }
    }

    @Test
    fun `should refuse to withdraw a request the business already approved`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val request = AppointmentRequest.stub(initiatorUserId = userId).copy(status = AppointmentRequestStatus.APPROVED)
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { requestDataSource.getForUpdate(request.id) } returns request
        }

        whenn()
        val result = fixture.sut(userId, request.id)

        then()
        assertTrue(result.exceptionOrNull() is AppointmentRequestStatusError.AlreadyApproved)
        coVerify(exactly = 0) { fixture.requestDataSource.withdraw(any()) }
    }

    @Test
    fun `should refuse to withdraw a request that was already declined or cancelled`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val request = AppointmentRequest.stub(
            initiatorUserId = userId,
        ).copy(status = AppointmentRequestStatus.CANCELLED)
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { requestDataSource.getForUpdate(request.id) } returns request
        }

        whenn()
        val result = fixture.sut(userId, request.id)

        then()
        assertTrue(result.exceptionOrNull() is AppointmentRequestStatusError.AlreadyDeclined)
        coVerify(exactly = 0) { fixture.requestDataSource.withdraw(any()) }
    }
}
