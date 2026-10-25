package com.bookk.business.domain.impl.operation.appointment

import com.bookk.business.domain.api.appointment.operation.GetAppointmentRescheduleContext
import com.bookk.business.domain.api.employee.entity.Employee
import com.bookk.business.domain.api.service.entity.Service
import com.bookk.business.domain.datasource.EmployeeDataSource
import com.bookk.business.domain.datasource.ServiceDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class GetAppointmentRescheduleContextImplTest {

    private class SutFixture {
        val employeeDataSource = mockk<EmployeeDataSource>()
        val serviceDataSource = mockk<ServiceDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val sut = GetAppointmentRescheduleContextImpl(employeeDataSource, serviceDataSource, transactionManager)
    }

    @Test
    fun `should resolve the employee and services`() = runUnitTest {
        given()
        val businessId = Uuid.random()
        val employee = Employee.stub(businessId = businessId)
        val service = Service.stub(businessId = businessId)
        val fixture = SutFixture()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { employeeDataSource.getEmployee(businessId, employee.id) } returns employee
            coEvery { serviceDataSource.getServicesByIds(listOf(service.id)) } returns listOf(service)
        }

        whenn()
        val result = fixture.sut.invoke(businessId, employee.id, listOf(service.id, service.id))

        then()
        assertEquals(employee, result.getOrNull()?.employee)
        assertEquals(listOf(service, service), result.getOrNull()?.services)
    }

    @Test
    fun `should skip the employee when none is requested`() = runUnitTest {
        given()
        val businessId = Uuid.random()
        val service = Service.stub(businessId = businessId)
        val fixture = SutFixture()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { serviceDataSource.getServicesByIds(listOf(service.id)) } returns listOf(service)
        }

        whenn()
        val result = fixture.sut.invoke(businessId, null, listOf(service.id))

        then()
        assertNull(result.getOrNull()?.employee)
        assertEquals(listOf(service), result.getOrNull()?.services)
        coVerify(exactly = 0) { fixture.employeeDataSource.getEmployee(any(), any()) }
    }

    @Test
    fun `should skip services when none are requested`() = runUnitTest {
        given()
        val businessId = Uuid.random()
        val employee = Employee.stub(businessId = businessId)
        val fixture = SutFixture()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { employeeDataSource.getEmployee(businessId, employee.id) } returns employee
        }

        whenn()
        val result = fixture.sut.invoke(businessId, employee.id, emptyList())

        then()
        assertEquals(emptyList<Service>(), result.getOrNull()?.services)
        coVerify(exactly = 0) { fixture.serviceDataSource.getServicesByIds(any()) }
    }

    @Test
    fun `should return failure when the employee does not exist`() = runUnitTest {
        given()
        val businessId = Uuid.random()
        val employeeId = Uuid.random()
        val fixture = SutFixture()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { employeeDataSource.getEmployee(businessId, employeeId) } returns null
        }

        whenn()
        val result = fixture.sut.invoke(businessId, employeeId, emptyList())

        then()
        assertTrue(result.exceptionOrNull() is GetAppointmentRescheduleContext.Error.EmployeeNotFound)
    }

    @Test
    fun `should return failure when the employee is suspended`() = runUnitTest {
        given()
        val businessId = Uuid.random()
        val employee = Employee.stub(businessId = businessId, suspendedAt = Instant.fromEpochMilliseconds(0))
        val fixture = SutFixture()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { employeeDataSource.getEmployee(businessId, employee.id) } returns employee
        }

        whenn()
        val result = fixture.sut.invoke(businessId, employee.id, emptyList())

        then()
        assertTrue(result.exceptionOrNull() is GetAppointmentRescheduleContext.Error.EmployeeSuspended)
    }

    @Test
    fun `should return failure when a service is missing or belongs to another business`() = runUnitTest {
        given()
        val businessId = Uuid.random()
        val foreignService = Service.stub(businessId = Uuid.random())
        val fixture = SutFixture()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { serviceDataSource.getServicesByIds(listOf(foreignService.id)) } returns listOf(foreignService)
        }

        whenn()
        val result = fixture.sut.invoke(businessId, null, listOf(foreignService.id))

        then()
        assertTrue(result.exceptionOrNull() is GetAppointmentRescheduleContext.Error.ServiceNotFound)
    }
}
