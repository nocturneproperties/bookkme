package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.api.entity.Appointment
import com.bookk.appointments.domain.api.entity.AppointmentSettings
import com.bookk.appointments.domain.datasource.AppointmentDataSource
import com.bookk.appointments.domain.datasource.AppointmentPermissionDataSource
import com.bookk.appointments.domain.datasource.AppointmentSettingsDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.domain.entity.Error
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atStartOfDayIn
import library.permissions.ResourcePermission
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.days
import kotlin.uuid.Uuid

internal class GetAppointmentsForDateImplTest {

    private class SutFixture {
        val appointmentDataSource = mockk<AppointmentDataSource>()
        val appointmentPermissionDataSource = mockk<AppointmentPermissionDataSource>()
        val settingsDataSource = mockk<AppointmentSettingsDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val sut = GetAppointmentsForDateImpl(appointmentDataSource, appointmentPermissionDataSource, settingsDataSource, transactionManager)
    }

    @Test
    fun `should return appointments when user has read permissions`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val businessId = Uuid.random()
        val date = LocalDate(2024, 1, 15)
        val settings = AppointmentSettings.stub(businessId)
        val instant = date.atStartOfDayIn(settings.timeZone)
        val expectedRange = instant..(instant + 1.days)
        val appointments = listOf(Appointment.stub(userId = userId, businessId = businessId))

        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { appointmentPermissionDataSource.getPermission(userId, businessId) } returns ResourcePermission(view = true, update = false, delete = false)
            coEvery { settingsDataSource.get(businessId) } returns settings
            coEvery { appointmentDataSource.getAllForDate(businessId, expectedRange, null) } returns appointments
        }

        whenn()
        val result = fixture.sut(userId, businessId, date, employeeId = null)

        then()
        assertTrue(result.isSuccess)
        assertEquals(appointments, result.getOrNull())
    }

    @Test
    fun `should return only appointments of requested employee`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val businessId = Uuid.random()
        val employeeId = Uuid.random()
        val date = LocalDate(2024, 1, 15)
        val settings = AppointmentSettings.stub(businessId)
        val instant = date.atStartOfDayIn(settings.timeZone)
        val expectedRange = instant..(instant + 1.days)
        val appointments = listOf(Appointment.stub(businessId = businessId))

        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { appointmentPermissionDataSource.getPermission(userId, businessId) } returns ResourcePermission(view = true, update = false, delete = false)
            coEvery { settingsDataSource.get(businessId) } returns settings
            coEvery { appointmentDataSource.getAllForDate(businessId, expectedRange, employeeId) } returns appointments
        }

        whenn()
        val result = fixture.sut(userId, businessId, date, employeeId = employeeId)

        then()
        assertTrue(result.isSuccess)
        assertEquals(appointments, result.getOrNull())
    }

    @Test
    fun `should return failure when user has no permissions`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val businessId = Uuid.random()
        val date = LocalDate(2024, 1, 15)

        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { appointmentPermissionDataSource.getPermission(userId, businessId) } returns ResourcePermission.NONE
        }

        whenn()
        val result = fixture.sut(userId, businessId, date, employeeId = null)

        then()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is Error.OperationNotAllowed)
    }

    @Test
    fun `should return not found when settings are missing`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val businessId = Uuid.random()
        val date = LocalDate(2024, 1, 15)

        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { appointmentPermissionDataSource.getPermission(userId, businessId) } returns ResourcePermission(view = true, update = false, delete = false)
            coEvery { settingsDataSource.get(businessId) } returns null
        }

        whenn()
        val result = fixture.sut(userId, businessId, date, employeeId = null)

        then()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is Error.NotFound)
    }

    @Test
    fun `should return failure when data source fails`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val userId = Uuid.random()
        val businessId = Uuid.random()
        val date = LocalDate(2024, 1, 15)
        val settings = AppointmentSettings.stub(businessId)
        val instant = date.atStartOfDayIn(settings.timeZone)
        val expectedRange = instant..(instant + 1.days)
        val exception = RuntimeException("Database error")

        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { appointmentPermissionDataSource.getPermission(userId, businessId) } returns ResourcePermission(view = true, update = false, delete = false)
            coEvery { settingsDataSource.get(businessId) } returns settings
            coEvery { appointmentDataSource.getAllForDate(businessId, expectedRange, null) } throws exception
        }

        whenn()
        val result = fixture.sut(userId, businessId, date, employeeId = null)

        then()
        assertTrue(result.isFailure)
        assertEquals(exception, result.exceptionOrNull())
    }
}
