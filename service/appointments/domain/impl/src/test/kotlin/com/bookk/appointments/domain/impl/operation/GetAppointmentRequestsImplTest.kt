package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.api.entity.AppointmentRequest
import com.bookk.appointments.domain.datasource.AppointmentRequestDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.mockk.coEvery
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class GetAppointmentRequestsImplTest {

    private class SutFixture {
        val requestsDataSource = mockk<AppointmentRequestDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val sut = GetAppointmentRequestsImpl(requestsDataSource, transactionManager)
    }

    @Test
    fun `should return pending appointment requests made by the user in the business`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val businessId = Uuid.random()
        val requests = listOf(AppointmentRequest.stub(userId = userId, businessId = businessId))
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { requestsDataSource.getPendingByUser(userId, businessId) } returns requests
        }

        whenn()
        val result = fixture.sut(userId, businessId)

        then()
        assertTrue(result.isSuccess)
        assertEquals(requests, result.getOrNull())
    }

    @Test
    fun `should return failure when data source fails`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val businessId = Uuid.random()
        val exception = RuntimeException("Database error")
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { requestsDataSource.getPendingByUser(userId, businessId) } throws exception
        }

        whenn()
        val result = fixture.sut(userId, businessId)

        then()
        assertTrue(result.isFailure)
        assertEquals(exception, result.exceptionOrNull())
    }
}
