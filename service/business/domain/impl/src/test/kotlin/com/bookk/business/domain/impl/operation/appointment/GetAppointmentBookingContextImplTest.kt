package com.bookk.business.domain.impl.operation.appointment

import com.bookk.business.domain.api.appointment.operation.GetAppointmentBookingContext
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
import com.bookk.server.user.client.UserClient
import com.bookk.server.user.client.api.UserSnapshot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class GetAppointmentBookingContextImplTest {

    private class SutFixture {
        val employeeDataSource = mockk<EmployeeDataSource>()
        val clientDataSource = mockk<ClientDataSource>()
        val serviceDataSource = mockk<ServiceDataSource>()
        val userClient = mockk<UserClient>()
        val transactionManager = mockk<TransactionManager>()
        val sut = GetAppointmentBookingContextImpl(employeeDataSource, clientDataSource, serviceDataSource, userClient, transactionManager)
    }

    @Test
    fun `should return booking context when employee, client and services all exist`() = runUnitTest {
        given()
        val businessId = Uuid.random()
        val employeeId = Uuid.random()
        val userId = Uuid.random()
        val employee = Employee.stub(id = employeeId, businessId = businessId)
        val client = Client.Integrated.stub(userId = userId)
        val service = Service.stub(businessId = businessId)
        val fixture = SutFixture()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { employeeDataSource.getEmployee(businessId, employeeId) } returns employee
            coEvery { clientDataSource.getClientByUserId(businessId, userId) } returns client
            coEvery { serviceDataSource.getServicesByIds(listOf(service.id)) } returns listOf(service)
        }

        whenn()
        val result = fixture.sut.invoke(businessId, employeeId, userId, listOf(service.id))

        then()
        assertTrue(result.isSuccess)
        val context = result.getOrNull()!!
        assertEquals(employee, context.employee)
        assertEquals(client.id, context.client.id)
        assertEquals(client.name, context.client.name)
        assertEquals(client.lastName, context.client.lastName)
        assertEquals(client.phone, context.client.phone)
        assertEquals(client.email, context.client.email)
        assertEquals(listOf(service), context.services)
        coVerify(exactly = 0) { fixture.userClient.getUserById(any()) }
        coVerify(exactly = 0) { fixture.clientDataSource.getOrCreateIntegratedClient(any(), any()) }
    }

    @Test
    fun `should create an integrated client from the user profile when no client exists for this user yet`() = runUnitTest {
        given()
        val businessId = Uuid.random()
        val employeeId = Uuid.random()
        val userId = Uuid.random()
        val employee = Employee.stub(id = employeeId, businessId = businessId)
        val user = UserSnapshot.stub(id = userId, name = "Bob", lastName = "Jones", email = "bob@test.com", phone = "+123456789")
        val createdClient = Client.Integrated.stub(name = user.name, lastName = user.lastName, phone = user.phone!!, email = user.email, userId = userId)
        val service = Service.stub(businessId = businessId)
        val fixture = SutFixture()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { employeeDataSource.getEmployee(businessId, employeeId) } returns employee
            coEvery { clientDataSource.getClientByUserId(businessId, userId) } returns null
            coEvery { userClient.getUserById(userId) } returns Result.success(user)
            coEvery { clientDataSource.getOrCreateIntegratedClient(businessId, any()) } returns createdClient
            coEvery { serviceDataSource.getServicesByIds(listOf(service.id)) } returns listOf(service)
        }

        whenn()
        val result = fixture.sut.invoke(businessId, employeeId, userId, listOf(service.id))

        then()
        assertTrue(result.isSuccess)
        val context = result.getOrNull()!!
        assertEquals(createdClient.id, context.client.id)
        assertEquals(createdClient.name, context.client.name)
        assertEquals(createdClient.phone, context.client.phone)
        coVerify(exactly = 1) {
            fixture.clientDataSource.getOrCreateIntegratedClient(
                businessId,
                match<Client.Integrated> {
                    it.name == user.name && it.lastName == user.lastName && it.phone == user.phone && it.email == user.email && it.userId == userId
                }
            )
        }
    }

    @Test
    fun `should create an integrated client with no phone when the user profile has none`() = runUnitTest {
        given()
        val businessId = Uuid.random()
        val employeeId = Uuid.random()
        val userId = Uuid.random()
        val employee = Employee.stub(id = employeeId, businessId = businessId)
        val user = UserSnapshot.stub(id = userId, phone = null)
        val createdClient = Client.Integrated.stub(name = user.name, lastName = user.lastName, phone = null, email = user.email, userId = userId)
        val service = Service.stub(businessId = businessId)
        val fixture = SutFixture()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { employeeDataSource.getEmployee(businessId, employeeId) } returns employee
            coEvery { clientDataSource.getClientByUserId(businessId, userId) } returns null
            coEvery { userClient.getUserById(userId) } returns Result.success(user)
            coEvery { clientDataSource.getOrCreateIntegratedClient(businessId, any()) } returns createdClient
            coEvery { serviceDataSource.getServicesByIds(listOf(service.id)) } returns listOf(service)
        }

        whenn()
        val result = fixture.sut.invoke(businessId, employeeId, userId, listOf(service.id))

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) {
            fixture.clientDataSource.getOrCreateIntegratedClient(
                businessId,
                match<Client.Integrated> {
                    it.name == user.name && it.lastName == user.lastName && it.phone == null && it.email == user.email && it.userId == userId
                }
            )
        }
    }

    @Test
    fun `should expand a repeated service id into one context line per requested count`() = runUnitTest {
        given()
        val businessId = Uuid.random()
        val employeeId = Uuid.random()
        val userId = Uuid.random()
        val employee = Employee.stub(id = employeeId, businessId = businessId)
        val client = Client.Integrated.stub(userId = userId)
        val serviceX = Service.stub(businessId = businessId)
        val serviceIds = listOf(serviceX.id, serviceX.id, serviceX.id, serviceX.id, serviceX.id)
        val fixture = SutFixture()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { employeeDataSource.getEmployee(businessId, employeeId) } returns employee
            coEvery { clientDataSource.getClientByUserId(businessId, userId) } returns client
            coEvery { serviceDataSource.getServicesByIds(listOf(serviceX.id)) } returns listOf(serviceX)
        }

        whenn()
        val result = fixture.sut.invoke(businessId, employeeId, userId, serviceIds)

        then()
        assertTrue(result.isSuccess)
        val context = result.getOrNull()!!
        assertEquals(List(5) { serviceX }, context.services)
    }

    @Test
    fun `should return EmptyServiceList when service ids list is empty`() = runUnitTest {
        given()
        val businessId = Uuid.random()
        val employeeId = Uuid.random()
        val userId = Uuid.random()
        val fixture = SutFixture()

        whenn()
        val result = fixture.sut.invoke(businessId, employeeId, userId, emptyList())

        then()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is GetAppointmentBookingContext.Error.EmptyServiceList)
        coVerify(exactly = 0) { fixture.employeeDataSource.getEmployee(any(), any()) }
    }

    @Test
    fun `should return failure when employee is suspended`() = runUnitTest {
        given()
        val businessId = Uuid.random()
        val userId = Uuid.random()
        val employee = Employee.stub(businessId = businessId, suspendedAt = Instant.fromEpochMilliseconds(1))
        val fixture = SutFixture()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { employeeDataSource.getEmployee(businessId, employee.id) } returns employee
        }

        whenn()
        val result = fixture.sut.invoke(businessId, employee.id, userId, listOf(Uuid.random()))

        then()
        assertTrue(result.exceptionOrNull() is GetAppointmentBookingContext.Error.EmployeeSuspended)
        coVerify(exactly = 0) { fixture.clientDataSource.getClientByUserId(any(), any()) }
    }

    @Test
    fun `should return failure when employee not found`() = runUnitTest {
        given()
        val businessId = Uuid.random()
        val employeeId = Uuid.random()
        val userId = Uuid.random()
        val fixture = SutFixture()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { employeeDataSource.getEmployee(businessId, employeeId) } returns null
        }

        whenn()
        val result = fixture.sut.invoke(businessId, employeeId, userId, listOf(Uuid.random()))

        then()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is GetAppointmentBookingContext.Error.EmployeeNotFound)
    }

    @Test
    fun `should return failure when some services are not found`() = runUnitTest {
        given()
        val businessId = Uuid.random()
        val employeeId = Uuid.random()
        val userId = Uuid.random()
        val employee = Employee.stub(id = employeeId, businessId = businessId)
        val client = Client.Integrated.stub(userId = userId)
        val serviceIds = listOf(Uuid.random(), Uuid.random())
        val fixture = SutFixture()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { employeeDataSource.getEmployee(businessId, employeeId) } returns employee
            coEvery { clientDataSource.getClientByUserId(businessId, userId) } returns client
            coEvery { serviceDataSource.getServicesByIds(serviceIds) } returns listOf(Service.stub(businessId = businessId))
        }

        whenn()
        val result = fixture.sut.invoke(businessId, employeeId, userId, serviceIds)

        then()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is GetAppointmentBookingContext.Error.ServiceNotFound)
    }

    @Test
    fun `should return failure when a resolved service belongs to another business`() = runUnitTest {
        given()
        val businessId = Uuid.random()
        val employeeId = Uuid.random()
        val userId = Uuid.random()
        val employee = Employee.stub(id = employeeId, businessId = businessId)
        val client = Client.Integrated.stub(userId = userId)
        val foreignService = Service.stub(businessId = Uuid.random())
        val fixture = SutFixture()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { employeeDataSource.getEmployee(businessId, employeeId) } returns employee
            coEvery { clientDataSource.getClientByUserId(businessId, userId) } returns client
            coEvery { serviceDataSource.getServicesByIds(listOf(foreignService.id)) } returns listOf(foreignService)
        }

        whenn()
        val result = fixture.sut.invoke(businessId, employeeId, userId, listOf(foreignService.id))

        then()
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is GetAppointmentBookingContext.Error.ServiceNotFound)
    }
}
