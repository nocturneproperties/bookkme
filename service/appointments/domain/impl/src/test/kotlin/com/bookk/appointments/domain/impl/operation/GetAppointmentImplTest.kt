package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.api.entity.Appointment
import com.bookk.appointments.domain.datasource.AppointmentDataSource
import com.bookk.appointments.domain.datasource.AppointmentPermissionDataSource
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

internal class GetAppointmentImplTest {

    private class SutFixture {
        val appointmentDataSource = mockk<AppointmentDataSource>()
        val appointmentPermissionDataSource = mockk<AppointmentPermissionDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val sut = GetAppointmentImpl(appointmentDataSource, appointmentPermissionDataSource, transactionManager)
    }

    @Test
    fun `should return appointment when caller has view permission`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val appointment = Appointment.stub()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { appointmentDataSource.get(appointment.id) } returns appointment
            coEvery { appointmentPermissionDataSource.getPermission(userId, appointment.businessId) } returns ResourcePermission(view = true, update = false, delete = false)
        }

        whenn()
        val result = fixture.sut(userId, appointment.id)

        then()
        assertEquals(appointment, result.getOrThrow())
    }

    @Test
    fun `should deny access when caller has no permission`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val appointment = Appointment.stub()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { appointmentDataSource.get(appointment.id) } returns appointment
            coEvery { appointmentPermissionDataSource.getPermission(userId, appointment.businessId) } returns ResourcePermission.NONE
        }

        whenn()
        val result = fixture.sut(userId, appointment.id)

        then()
        assertTrue(result.exceptionOrNull() is Error.OperationNotAllowed)
    }

    @Test
    fun `should deny access when caller has update and delete but no view permission`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val appointment = Appointment.stub()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { appointmentDataSource.get(appointment.id) } returns appointment
            coEvery { appointmentPermissionDataSource.getPermission(userId, appointment.businessId) } returns ResourcePermission(view = false, update = true, delete = true)
        }

        whenn()
        val result = fixture.sut(userId, appointment.id)

        then()
        assertTrue(result.exceptionOrNull() is Error.OperationNotAllowed)
    }

    @Test
    fun `should return failure when caller is suspended`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val appointment = Appointment.stub()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { appointmentDataSource.get(appointment.id) } returns appointment
            coEvery { appointmentPermissionDataSource.getPermission(userId, appointment.businessId) } throws EmployeeAccessSuspended()
        }

        whenn()
        val result = fixture.sut(userId, appointment.id)

        then()
        assertTrue(result.exceptionOrNull() is EmployeeAccessSuspended)
    }

    @Test
    fun `should return not found when appointment is missing`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val appointmentId = Uuid.random()
        val error = Error.NotFound()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { appointmentDataSource.get(appointmentId) } throws error
        }

        whenn()
        val result = fixture.sut(userId, appointmentId)

        then()
        assertTrue(result.exceptionOrNull() is Error.NotFound)
        coVerify(exactly = 0) { fixture.appointmentPermissionDataSource.getPermission(any(), any()) }
    }

    @Test
    fun `should propagate unexpected datasource failures`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val appointmentId = Uuid.random()
        val error = IllegalStateException("Database unavailable")
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { appointmentDataSource.get(appointmentId) } throws error
        }

        whenn()
        val result = fixture.sut(userId, appointmentId)

        then()
        assertEquals(error, result.exceptionOrNull())
        coVerify(exactly = 0) { fixture.appointmentPermissionDataSource.getPermission(any(), any()) }
    }
}
