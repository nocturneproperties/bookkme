package com.bookk.business.domain.impl.operation.appointment

import com.bookk.business.domain.api.appointment.operation.GetAppointmentBookingContext
import com.bookk.business.domain.api.client.entity.Client
import com.bookk.business.domain.api.employee.entity.Employee
import com.bookk.business.domain.api.service.entity.Service
import com.bookk.business.domain.datasource.ClientDataSource
import com.bookk.business.domain.datasource.EmployeeDataSource
import com.bookk.business.domain.datasource.ServiceDataSource
import com.bookk.core.data.eventstreaming.StandardEventProducer
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.server.business.client.api.event.BusinessEvent
import com.bookk.server.user.client.UserClient
import com.bookk.server.user.client.api.UserSnapshot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
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
        val eventProducer = mockk<StandardEventProducer>(relaxed = true)
        val sut = GetAppointmentBookingContextImpl(employeeDataSource, clientDataSource, serviceDataSource, userClient, transactionManager, eventProducer)

        init {
            coEvery { clientDataSource.getClientsByContacts(any(), any(), any()) } returns emptyList()
        }

        fun bookingByNewClient(businessId: Uuid, employeeId: Uuid, user: UserSnapshot, service: Service) {
            transactionManager.mockTransaction()
            coEvery { employeeDataSource.getEmployee(businessId, employeeId) } returns Employee.stub(id = employeeId, businessId = businessId)
            coEvery { clientDataSource.getClientByUserId(businessId, user.id) } returns null
            coEvery { userClient.getUserById(user.id) } returns Result.success(user)
            coEvery { serviceDataSource.getServicesByIds(listOf(service.id)) } returns listOf(service)
        }
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

    @Test
    fun `should take over the detached client holding the user's email instead of creating a new one`() = runUnitTest {
        given()
        val businessId = Uuid.random()
        val employeeId = Uuid.random()
        val user = UserSnapshot.stub(id = Uuid.random(), email = "user@test.com", phone = "+123456789")
        val service = Service.stub(businessId = businessId)
        val detached = Client.Detached.stub(phone = null, email = "user@test.com", description = "Allergic to latex")
        val fixture = SutFixture()
        with(fixture) {
            bookingByNewClient(businessId, employeeId, user, service)
            coEvery { clientDataSource.getClientsByContacts(businessId, listOf("+123456789"), listOf("user@test.com")) } returns listOf(detached)
            coEvery { clientDataSource.takeOverClient(businessId, detached.id, any()) } returns
                Client.Integrated.stub(id = detached.id, userId = user.id, description = detached.description)
        }

        whenn()
        val result = fixture.sut.invoke(businessId, employeeId, user.id, listOf(service.id))

        then()
        assertEquals(detached.id, result.getOrThrow().client.id)
        coVerify(exactly = 1) {
            fixture.clientDataSource.takeOverClient(
                businessId,
                detached.id,
                match { it.userId == user.id && it.name == user.name && it.email == user.email && it.phone == user.phone }
            )
        }
        coVerify(exactly = 0) { fixture.clientDataSource.getOrCreateIntegratedClient(any(), any()) }
        coVerify(exactly = 0) { fixture.eventProducer.send(any<BusinessEvent.ClientMerged>(), any()) }
    }

    @Test
    fun `should take over the detached client holding the user's phone when none holds the email`() = runUnitTest {
        given()
        val businessId = Uuid.random()
        val employeeId = Uuid.random()
        val user = UserSnapshot.stub(id = Uuid.random(), email = "user@test.com", phone = "+123456789")
        val service = Service.stub(businessId = businessId)
        val detached = Client.Detached.stub(phone = "+123456789", email = null)
        val fixture = SutFixture()
        with(fixture) {
            bookingByNewClient(businessId, employeeId, user, service)
            coEvery { clientDataSource.getClientsByContacts(businessId, any(), any()) } returns listOf(detached)
            coEvery { clientDataSource.takeOverClient(businessId, detached.id, any()) } returns
                Client.Integrated.stub(id = detached.id, userId = user.id)
        }

        whenn()
        val result = fixture.sut.invoke(businessId, employeeId, user.id, listOf(service.id))

        then()
        assertEquals(detached.id, result.getOrThrow().client.id)
    }

    @Test
    fun `should keep the detached client holding the email and merge the one holding the phone into it`() = runUnitTest {
        given()
        val businessId = Uuid.random()
        val employeeId = Uuid.random()
        val user = UserSnapshot.stub(id = Uuid.random(), email = "user@test.com", phone = "+123456789")
        val service = Service.stub(businessId = businessId)
        val byPhone = Client.Detached.stub(phone = "+123456789", email = null)
        val byEmail = Client.Detached.stub(phone = null, email = "user@test.com")
        val fixture = SutFixture()
        with(fixture) {
            bookingByNewClient(businessId, employeeId, user, service)
            coEvery { clientDataSource.getClientsByContacts(businessId, any(), any()) } returns listOf(byPhone, byEmail)
            coEvery { clientDataSource.deleteClient(businessId, byPhone.id) } returns true
            coEvery { clientDataSource.takeOverClient(businessId, byEmail.id, any()) } returns
                Client.Integrated.stub(id = byEmail.id, userId = user.id)
        }

        whenn()
        val result = fixture.sut.invoke(businessId, employeeId, user.id, listOf(service.id))

        then()
        assertEquals(byEmail.id, result.getOrThrow().client.id)
        coVerifyOrder {
            fixture.clientDataSource.deleteClient(businessId, byPhone.id)
            fixture.eventProducer.send(
                match<BusinessEvent.ClientMerged> {
                    it.businessId == businessId && it.fromClientId == byPhone.id && it.toClientId == byEmail.id
                },
                any()
            )
            fixture.clientDataSource.takeOverClient(businessId, byEmail.id, any())
        }
    }

    @Test
    fun `should not take over an integrated client holding the user's contacts`() = runUnitTest {
        given()
        val businessId = Uuid.random()
        val employeeId = Uuid.random()
        val user = UserSnapshot.stub(id = Uuid.random(), email = "user@test.com", phone = "+123456789")
        val service = Service.stub(businessId = businessId)
        val created = Client.Integrated.stub(userId = user.id)
        val fixture = SutFixture()
        with(fixture) {
            bookingByNewClient(businessId, employeeId, user, service)
            coEvery { clientDataSource.getClientsByContacts(businessId, any(), any()) } returns
                listOf(Client.Integrated.stub(phone = "+123456789", email = null))
            coEvery { clientDataSource.getOrCreateIntegratedClient(businessId, any()) } returns created
        }

        whenn()
        val result = fixture.sut.invoke(businessId, employeeId, user.id, listOf(service.id))

        then()
        assertEquals(created.id, result.getOrThrow().client.id)
        coVerify(exactly = 0) { fixture.clientDataSource.takeOverClient(any(), any(), any()) }
    }

    @Test
    fun `should create an integrated client when the detached one was taken over concurrently`() = runUnitTest {
        given()
        val businessId = Uuid.random()
        val employeeId = Uuid.random()
        val user = UserSnapshot.stub(id = Uuid.random(), email = "user@test.com", phone = null)
        val service = Service.stub(businessId = businessId)
        val detached = Client.Detached.stub(phone = null, email = "user@test.com")
        val created = Client.Integrated.stub(userId = user.id)
        val fixture = SutFixture()
        with(fixture) {
            bookingByNewClient(businessId, employeeId, user, service)
            coEvery { clientDataSource.getClientsByContacts(businessId, emptyList(), listOf("user@test.com")) } returns listOf(detached)
            coEvery { clientDataSource.takeOverClient(businessId, detached.id, any()) } returns null
            coEvery { clientDataSource.getOrCreateIntegratedClient(businessId, any()) } returns created
        }

        whenn()
        val result = fixture.sut.invoke(businessId, employeeId, user.id, listOf(service.id))

        then()
        assertEquals(created.id, result.getOrThrow().client.id)
    }
}
