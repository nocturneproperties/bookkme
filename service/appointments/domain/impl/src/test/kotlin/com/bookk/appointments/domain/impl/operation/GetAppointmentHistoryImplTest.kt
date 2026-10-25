package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.api.entity.Appointment
import com.bookk.appointments.domain.api.entity.AppointmentPagination
import com.bookk.appointments.domain.datasource.AppointmentDataSource
import com.bookk.appointments.domain.datasource.AppointmentPermissionDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.domain.entity.Error
import com.bookk.core.domain.entity.PaginationMetadata
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.mockk.coEvery
import io.mockk.mockk
import library.permissions.ResourcePermission
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class GetAppointmentHistoryImplTest {

    private class SutFixture {
        val dataSource = mockk<AppointmentDataSource>()
        val appointmentPermissionDataSource = mockk<AppointmentPermissionDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val sut = GetAppointmentHistoryImpl(dataSource, appointmentPermissionDataSource, transactionManager)
    }

    @Test
    fun `should return paginated appointments when user has read permissions`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val businessId = Uuid.random()
        val appointments = listOf(Appointment.stub(userId = userId, businessId = businessId))
        val pagination = AppointmentPagination(
            data = appointments,
            metadata = PaginationMetadata(total = 1L, page = 0L, pageSize = 50)
        )

        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { appointmentPermissionDataSource.getPermission(userId, businessId) } returns ResourcePermission(view = true, update = false, delete = false)
            coEvery { dataSource.getAllPaginated(businessId, 50, 0, null) } returns pagination
        }

        whenn()
        val result = fixture.sut(userId, businessId, limit = 50, offset = 0)

        then()
        assertTrue(result.isSuccess)
        assertEquals(pagination, result.getOrNull())
    }

    @Test
    fun `should pass query filter to data source`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val businessId = Uuid.random()
        val appointments = listOf(Appointment.stub(userId = userId, businessId = businessId))
        val pagination = AppointmentPagination(
            data = appointments,
            metadata = PaginationMetadata(total = 1L, page = 0L, pageSize = 50)
        )

        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { appointmentPermissionDataSource.getPermission(userId, businessId) } returns ResourcePermission(view = true, update = false, delete = false)
            coEvery {
                dataSource.getAllPaginated(businessId, 50, 0, "John")
            } returns pagination
        }

        whenn()
        val result = fixture.sut(
            userId,
            businessId,
            limit = 50,
            offset = 0,
            query = "John"
        )

        then()
        assertTrue(result.isSuccess)
        assertEquals(pagination, result.getOrNull())
    }

    @Test
    fun `should return failure when user has no permissions`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val businessId = Uuid.random()

        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { appointmentPermissionDataSource.getPermission(userId, businessId) } returns ResourcePermission.NONE
        }

        whenn()
        val result = fixture.sut(userId, businessId, limit = 50, offset = 0)

        then()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is Error.OperationNotAllowed)
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
            coEvery { appointmentPermissionDataSource.getPermission(userId, businessId) } returns ResourcePermission(view = true, update = false, delete = false)
            coEvery { dataSource.getAllPaginated(businessId, 50, 0, null) } throws exception
        }

        whenn()
        val result = fixture.sut(userId, businessId, limit = 50, offset = 0)

        then()
        assertTrue(result.isFailure)
        assertEquals(exception, result.exceptionOrNull())
    }
}
