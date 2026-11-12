package com.bookk.business.domain.impl.operation.appointment

import com.bookk.business.domain.api.appointment.operation.GetStaffBookingContext
import com.bookk.business.domain.api.client.entity.Client
import com.bookk.business.domain.api.employee.entity.Employee
import com.bookk.business.domain.api.service.entity.Service
import com.bookk.business.domain.datasource.ClientDataSource
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class GetStaffBookingContextImplTest {

    private class SutFixture {
        val employeeDataSource = mockk<EmployeeDataSource>()
        val clientDataSource = mockk<ClientDataSource>()
        val serviceDataSource = mockk<ServiceDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val sut =
            GetStaffBookingContextImpl(employeeDataSource, clientDataSource, serviceDataSource, transactionManager)

        val businessId = Uuid.random()
        val employee = Employee.stub(businessId = businessId)
        val client = Client.Detached.stub()
        val service = Service.stub(businessId = businessId)

        init {
            transactionManager.mockTransaction()
            coEvery { employeeDataSource.getEmployee(businessId, employee.id) } returns employee
            coEvery { clientDataSource.getClientById(businessId, client.id) } returns client
            coEvery { serviceDataSource.getServicesByIds(listOf(service.id)) } returns listOf(service)
        }
    }

    @Test
    fun `should return the employee, the business's client and the services`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val result = fixture.sut(fixture.businessId, fixture.employee.id, fixture.client.id, listOf(fixture.service.id))

        then()
        val context = result.getOrThrow()
        assertEquals(fixture.employee, context.employee)
        assertEquals(fixture.client.id, context.client.id)
        assertEquals(fixture.client.name, context.client.name)
        assertEquals(fixture.client.lastName, context.client.lastName)
        assertEquals(fixture.client.phone, context.client.phone)
        assertEquals(fixture.client.email, context.client.email)
        assertEquals(listOf(fixture.service), context.services)
    }

    @Test
    fun `should carry the account of a client who has one`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val client = Client.Integrated.stub()
        coEvery { fixture.clientDataSource.getClientById(fixture.businessId, client.id) } returns client

        whenn()
        val result = fixture.sut(fixture.businessId, fixture.employee.id, client.id, listOf(fixture.service.id))

        then()
        assertEquals(client.userId, result.getOrThrow().client.userId)
    }

    @Test
    fun `should expand a repeated service id into one context line per requested count`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val serviceIds = List(3) { fixture.service.id }

        whenn()
        val result = fixture.sut(fixture.businessId, fixture.employee.id, fixture.client.id, serviceIds)

        then()
        assertEquals(List(3) { fixture.service }, result.getOrThrow().services)
    }

    @Test
    fun `should return failure when the client is not a client of the business`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val clientId = Uuid.random()
        coEvery { fixture.clientDataSource.getClientById(fixture.businessId, clientId) } returns null

        whenn()
        val result = fixture.sut(fixture.businessId, fixture.employee.id, clientId, listOf(fixture.service.id))

        then()
        assertTrue(result.exceptionOrNull() is GetStaffBookingContext.Error.ClientNotFound)
    }

    @Test
    fun `should return failure when the employee is not in the business`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val employeeId = Uuid.random()
        coEvery { fixture.employeeDataSource.getEmployee(fixture.businessId, employeeId) } returns null

        whenn()
        val result = fixture.sut(fixture.businessId, employeeId, fixture.client.id, listOf(fixture.service.id))

        then()
        assertTrue(result.exceptionOrNull() is GetStaffBookingContext.Error.EmployeeNotFound)
    }

    @Test
    fun `should return failure when the employee is suspended`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val employee = Employee.stub(businessId = fixture.businessId, suspendedAt = Instant.fromEpochMilliseconds(1))
        coEvery { fixture.employeeDataSource.getEmployee(fixture.businessId, employee.id) } returns employee

        whenn()
        val result = fixture.sut(fixture.businessId, employee.id, fixture.client.id, listOf(fixture.service.id))

        then()
        assertTrue(result.exceptionOrNull() is GetStaffBookingContext.Error.EmployeeSuspended)
        coVerify(exactly = 0) { fixture.clientDataSource.getClientById(any(), any()) }
    }

    @Test
    fun `should return failure when a service is not in the business`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val foreignService = Service.stub(businessId = Uuid.random())
        coEvery { fixture.serviceDataSource.getServicesByIds(listOf(foreignService.id)) } returns listOf(foreignService)

        whenn()
        val result = fixture.sut(fixture.businessId, fixture.employee.id, fixture.client.id, listOf(foreignService.id))

        then()
        assertTrue(result.exceptionOrNull() is GetStaffBookingContext.Error.ServiceNotFound)
    }

    @Test
    fun `should return failure when no services are requested`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val result = fixture.sut(fixture.businessId, fixture.employee.id, fixture.client.id, emptyList())

        then()
        assertTrue(result.exceptionOrNull() is GetStaffBookingContext.Error.EmptyServiceList)
        coVerify(exactly = 0) { fixture.employeeDataSource.getEmployee(any(), any()) }
    }
}
