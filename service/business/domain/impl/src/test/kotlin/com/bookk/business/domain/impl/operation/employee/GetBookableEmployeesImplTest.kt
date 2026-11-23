package com.bookk.business.domain.impl.operation.employee

import com.bookk.business.domain.api.business.entity.BusinessPermissions
import com.bookk.business.domain.api.employee.entity.BookableEmployee
import com.bookk.business.domain.api.employee.entity.Employee
import com.bookk.business.domain.api.service.entity.Service
import com.bookk.business.domain.datasource.EmployeeDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import library.schedule.Schedule
import library.schedule.WorkHour
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class GetBookableEmployeesImplTest {

    private val businessId = Uuid.random()

    private class SutFixture {
        val employeeDataSource = mockk<EmployeeDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val sut = GetBookableEmployeesImpl(employeeDataSource, transactionManager)
    }

    @Test
    fun `should return the active employees of the business with only their booking details`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.transactionManager.mockTransaction()
        val service = Service.stub(businessId = businessId)
        val schedule = Schedule(
            workingDays = listOf(DayOfWeek.MONDAY),
            workingHours = mapOf(DayOfWeek.MONDAY to listOf(WorkHour(LocalTime(9, 0), LocalTime(17, 0)))),
        )
        val employee = Employee.stub(
            businessId = businessId,
            name = "Anna",
            lastName = "Smith",
            services = listOf(service),
            schedule = schedule,
            permissions = BusinessPermissions.FULL,
        )
        coEvery { fixture.employeeDataSource.getActiveEmployees(businessId) } returns listOf(employee)

        whenn()
        val result = fixture.sut(businessId)

        then()
        val expected = BookableEmployee(
            id = employee.id,
            businessId = businessId,
            name = "Anna",
            lastName = "Smith",
            services = listOf(service),
            schedule = schedule,
        )
        assertEquals(listOf(expected), result.getOrNull())
    }

    @Test
    fun `should read only active employees so suspended staff are never offered for booking`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.transactionManager.mockTransaction()
        coEvery { fixture.employeeDataSource.getActiveEmployees(businessId) } returns emptyList()

        whenn()
        val result = fixture.sut(businessId)

        then()
        assertEquals(emptyList<BookableEmployee>(), result.getOrNull())
        coVerify(exactly = 1) { fixture.employeeDataSource.getActiveEmployees(businessId) }
        coVerify(exactly = 0) { fixture.employeeDataSource.getEmployees(any()) }
    }

    @Test
    fun `should keep the order of the employees it read`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.transactionManager.mockTransaction()
        val first = Employee.stub(businessId = businessId, createdAt = Instant.fromEpochMilliseconds(2))
        val second = Employee.stub(businessId = businessId, createdAt = Instant.fromEpochMilliseconds(1))
        coEvery { fixture.employeeDataSource.getActiveEmployees(businessId) } returns listOf(first, second)

        whenn()
        val result = fixture.sut(businessId)

        then()
        assertEquals(listOf(first.id, second.id), result.getOrNull()?.map { it.id })
    }
}
