package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.api.entity.Appointment
import com.bookk.appointments.domain.api.entity.AppointmentCompletedBy
import com.bookk.appointments.domain.api.entity.AppointmentStatus
import com.bookk.appointments.domain.api.entity.AppointmentStatusError
import com.bookk.appointments.domain.api.entity.EmployeeSnapshot
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
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.uuid.Uuid

internal class MarkAppointmentNoShowImplTest {

    private class SutFixture {
        val appointmentDataSource = mockk<AppointmentDataSource>()
        val appointmentPermissionDataSource = mockk<AppointmentPermissionDataSource>()
        val transactionManager = mockk<TransactionManager>()

        val sut = MarkAppointmentNoShowImpl(
            appointmentDataSource,
            appointmentPermissionDataSource,
            transactionManager
        )

        fun givenLockedAppointment(userId: Uuid, appointment: Appointment, permission: ResourcePermission = updatePermission) {
            transactionManager.mockTransaction()
            coEvery { appointmentDataSource.getForUpdate(appointment.id) } returns appointment
            coEvery { appointmentPermissionDataSource.getPermission(userId, appointment.businessId) } returns permission
        }

        companion object {
            val updatePermission = ResourcePermission(view = false, update = true, delete = false)
        }
    }

    @Test
    fun `should mark started scheduled appointment as no-show`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val appointment = Appointment.stub()
        val noShow = appointment.copy(status = AppointmentStatus.NO_SHOW)
        with(fixture) {
            givenLockedAppointment(userId, appointment)
            coEvery { appointmentDataSource.markNoShow(appointment.id) } returns noShow
        }

        whenn()
        val result = fixture.sut.invoke(userId, appointment.id)

        then()
        assertEquals(noShow, result.getOrNull())
        coVerify(exactly = 1) { fixture.appointmentDataSource.markNoShow(appointment.id) }
    }

    @Test
    fun `should mark completed appointment as no-show`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val appointment = Appointment.stub().copy(status = AppointmentStatus.COMPLETED, completedBy = AppointmentCompletedBy.SYSTEM)
        val noShow = appointment.copy(status = AppointmentStatus.NO_SHOW, completedBy = null)
        with(fixture) {
            givenLockedAppointment(userId, appointment)
            coEvery { appointmentDataSource.markNoShow(appointment.id) } returns noShow
        }

        whenn()
        val result = fixture.sut.invoke(userId, appointment.id)

        then()
        assertEquals(noShow, result.getOrNull())
    }

    @Test
    fun `should return already no-show appointment unchanged`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val appointment = Appointment.stub().copy(status = AppointmentStatus.NO_SHOW)
        fixture.givenLockedAppointment(userId, appointment)

        whenn()
        val result = fixture.sut.invoke(userId, appointment.id)

        then()
        assertEquals(appointment, result.getOrNull())
        coVerify(exactly = 0) { fixture.appointmentDataSource.markNoShow(any()) }
    }

    @Test
    fun `should mark own appointment as no-show with view permission`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val appointment = Appointment.stub().copy(employee = EmployeeSnapshot.stub(userId = userId))
        with(fixture) {
            givenLockedAppointment(userId, appointment, ResourcePermission(view = true, update = false, delete = false))
            coEvery { appointmentDataSource.markNoShow(appointment.id) } returns appointment.copy(status = AppointmentStatus.NO_SHOW)
        }

        whenn()
        val result = fixture.sut.invoke(userId, appointment.id)

        then()
        assertTrue(result.isSuccess)
    }

    @Test
    fun `should return failure when user has view permission but appointment belongs to another employee`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val appointment = Appointment.stub()
        fixture.givenLockedAppointment(userId, appointment, ResourcePermission(view = true, update = false, delete = false))

        whenn()
        val result = fixture.sut.invoke(userId, appointment.id)

        then()
        assertTrue(result.exceptionOrNull() is Error.OperationNotAllowed)
        coVerify(exactly = 0) { fixture.appointmentDataSource.markNoShow(any()) }
    }

    @Test
    fun `should return failure when caller is a suspended employee`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val appointment = Appointment.stub()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { appointmentDataSource.getForUpdate(appointment.id) } returns appointment
            coEvery { appointmentPermissionDataSource.getPermission(userId, appointment.businessId) } throws EmployeeAccessSuspended()
        }

        whenn()
        val result = fixture.sut.invoke(userId, appointment.id)

        then()
        assertTrue(result.exceptionOrNull() is EmployeeAccessSuspended)
        coVerify(exactly = 0) { fixture.appointmentDataSource.markNoShow(any()) }
    }

    @Test
    fun `should return failure when appointment is cancelled`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val appointment = Appointment.stub().copy(status = AppointmentStatus.CANCELLED)
        fixture.givenLockedAppointment(userId, appointment)

        whenn()
        val result = fixture.sut.invoke(userId, appointment.id)

        then()
        assertTrue(result.exceptionOrNull() is AppointmentStatusError.AlreadyCancelled)
        coVerify(exactly = 0) { fixture.appointmentDataSource.markNoShow(any()) }
    }

    @Test
    fun `should return failure when appointment has not started yet`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val appointment = Appointment.stub(date = Clock.System.now() + 1.hours)
        fixture.givenLockedAppointment(userId, appointment)

        whenn()
        val result = fixture.sut.invoke(userId, appointment.id)

        then()
        assertTrue(result.exceptionOrNull() is AppointmentStatusError.NotStarted)
        coVerify(exactly = 0) { fixture.appointmentDataSource.markNoShow(any()) }
    }

    @Test
    fun `should return failure when completed appointment has not started yet`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val appointment = Appointment.stub(date = Clock.System.now() + 1.hours).copy(status = AppointmentStatus.COMPLETED)
        fixture.givenLockedAppointment(userId, appointment)

        whenn()
        val result = fixture.sut.invoke(userId, appointment.id)

        then()
        assertTrue(result.exceptionOrNull() is AppointmentStatusError.NotStarted)
        coVerify(exactly = 0) { fixture.appointmentDataSource.markNoShow(any()) }
    }

    @Test
    fun `should return failure when appointment does not exist`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val appointmentId = Uuid.random()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { appointmentDataSource.getForUpdate(appointmentId) } throws Error.NotFound()
        }

        whenn()
        val result = fixture.sut.invoke(Uuid.random(), appointmentId)

        then()
        assertTrue(result.exceptionOrNull() is Error.NotFound)
    }
}
