package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.api.entity.AppointmentRequest
import com.bookk.appointments.domain.api.entity.EmployeeSnapshot
import com.bookk.appointments.domain.datasource.AppointmentPermissionDataSource
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
import io.mockk.mockk
import library.permissions.EmployeeAccessSuspended
import library.permissions.ResourcePermission
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class GetAssignedAppointmentRequestsImplTest {

    private class SutFixture {
        val requestsDataSource = mockk<AppointmentRequestDataSource>()
        val appointmentPermissionDataSource = mockk<AppointmentPermissionDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val sut = GetAssignedAppointmentRequestsImpl(
            requestsDataSource,
            appointmentPermissionDataSource,
            transactionManager,
        )
    }

    @Test
    fun `should return pending requests assigned to the caller with a view grant`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val businessId = Uuid.random()
        val requests = listOf(
            AppointmentRequest.stub(businessId = businessId, employee = EmployeeSnapshot.stub(userId = userId)),
        )
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { appointmentPermissionDataSource.getPermission(userId, businessId) } returns
                ResourcePermission(view = true, update = false, delete = false)
            coEvery { requestsDataSource.getPendingAssignedTo(userId, businessId) } returns requests
        }

        whenn()
        val result = fixture.sut(userId, businessId)

        then()
        assertTrue(result.isSuccess)
        assertEquals(requests, result.getOrNull())
    }

    @Test
    fun `should return failure without reading requests when the caller has no view grant`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val businessId = Uuid.random()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { appointmentPermissionDataSource.getPermission(userId, businessId) } returns
                ResourcePermission.NONE
        }

        whenn()
        val result = fixture.sut(userId, businessId)

        then()
        assertTrue(result.exceptionOrNull() is Error.OperationNotAllowed)
        coVerify(exactly = 0) { fixture.requestsDataSource.getPendingAssignedTo(any(), any()) }
    }

    @Test
    fun `should return failure when the caller is a suspended employee`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val businessId = Uuid.random()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { appointmentPermissionDataSource.getPermission(userId, businessId) } throws
                EmployeeAccessSuspended()
        }

        whenn()
        val result = fixture.sut(userId, businessId)

        then()
        assertTrue(result.exceptionOrNull() is EmployeeAccessSuspended)
        coVerify(exactly = 0) { fixture.requestsDataSource.getPendingAssignedTo(any(), any()) }
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
            coEvery { appointmentPermissionDataSource.getPermission(userId, businessId) } returns
                ResourcePermission(view = true, update = false, delete = false)
            coEvery { requestsDataSource.getPendingAssignedTo(userId, businessId) } throws exception
        }

        whenn()
        val result = fixture.sut(userId, businessId)

        then()
        assertTrue(result.isFailure)
        assertEquals(exception, result.exceptionOrNull())
    }
}
