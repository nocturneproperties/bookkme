package com.bookk.business.domain.impl.operation.demo

import com.bookk.business.domain.api.business.entity.Business
import com.bookk.business.domain.api.client.entity.Client
import com.bookk.business.domain.api.employee.entity.Employee
import com.bookk.business.domain.api.employee.entity.EmployeeUpdateModel
import com.bookk.business.domain.api.service.entity.Service
import com.bookk.business.domain.api.service.entity.ServiceGroup
import com.bookk.business.domain.api.user.entity.UserLimits
import com.bookk.business.domain.datasource.BusinessDataSource
import com.bookk.business.domain.datasource.BusinessLinkDataSource
import com.bookk.business.domain.datasource.ClientDataSource
import com.bookk.business.domain.datasource.EmployeeDataSource
import com.bookk.business.domain.datasource.ServiceDataSource
import com.bookk.business.domain.datasource.UserLimitsDataSource
import com.bookk.business.domain.impl.operation.business.BusinessFounder
import com.bookk.core.data.eventstreaming.StandardEventProducer
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.domain.entity.Language
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.server.business.client.api.event.BusinessEvent
import com.bookk.server.user.client.UserClient
import com.bookk.server.user.client.api.UserSnapshot
import com.bookk.user.domain.api.operation.GetUserById
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import io.mockk.slot
import kotlinx.datetime.TimeZone
import library.email.PlaceholderEmail
import org.joda.money.Money
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class SeedDemoBusinessImplTest {

    private class SutFixture {
        val userClient = mockk<UserClient>()
        val userLimitsDataSource = mockk<UserLimitsDataSource>()
        val businessDataSource = mockk<BusinessDataSource>()
        val employeeDataSource = mockk<EmployeeDataSource>()
        val businessLinkDataSource = mockk<BusinessLinkDataSource>(relaxUnitFun = true)
        val serviceDataSource = mockk<ServiceDataSource>()
        val clientDataSource = mockk<ClientDataSource>()
        val eventProducer = mockk<StandardEventProducer>()
        val transactionManager = mockk<TransactionManager>()
        val sut = SeedDemoBusinessImpl(
            userClient,
            userLimitsDataSource,
            businessDataSource,
            BusinessFounder(businessDataSource, employeeDataSource, businessLinkDataSource),
            serviceDataSource,
            employeeDataSource,
            clientDataSource,
            eventProducer,
            transactionManager,
        )
        val owner = UserSnapshot.stub(name = "Demo", lastName = "", email = PlaceholderEmail.forDemoUser(Uuid.random()))
        val business = Business.stub(ownerId = owner.id, demo = true)
        val createdServices = mutableListOf<Service>()
        val updatedEmployee = slot<EmployeeUpdateModel>()
        val createdClients = slot<List<Client.Detached>>()
        val sentInTransaction = mutableListOf<Boolean>()
        val sent = slot<BusinessEvent.DemoBusinessSeeded>()

        init {
            transactionManager.mockTransaction()
            coEvery { userClient.getUserById(owner.id) } returns Result.success(owner)
            coEvery { userLimitsDataSource.lockForUser(owner.id) } returns UserLimits.stub(owner.id)
            coEvery { businessDataSource.countBusinessesOwnedBy(owner.id) } returns 0
            coEvery { businessDataSource.createBusiness(owner.id, any(), any(), any(), true) } returns business
            coEvery { employeeDataSource.createEmployee(any()) } answers { firstArg() }
            coEvery { businessLinkDataSource.getLinkByNameForUpdate(any()) } returns null
            coEvery { serviceDataSource.createServiceGroup(any()) } answers { firstArg() }
            coEvery { serviceDataSource.createService(any()) } answers
                { firstArg<Service>().also(createdServices::add) }
            coEvery { employeeDataSource.updateEmployee(capture(updatedEmployee)) } answers { Employee.stub() }
            coEvery { clientDataSource.createDetachedClients(business.id, capture(createdClients)) } answers {
                secondArg<List<Client.Detached>>()
            }
            coEvery { eventProducer.send(capture(sent), any()) } coAnswers {
                sentInTransaction += transactionManager.isInTransaction()
            }
        }
    }

    @Test
    fun `should found a demo business with the sample name for the owner in the given time zone`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val result = fixture.sut(fixture.owner.id, "Europe/Warsaw", Language.EN)

        then()
        assertTrue(result.isSuccess) { result.toString() }
        coVerify(exactly = 1) {
            fixture.businessDataSource.createBusiness(
                fixture.owner.id,
                DemoBusinessSample.BUSINESS_NAME,
                "EUR",
                TimeZone.of("Europe/Warsaw"),
                true,
            )
        }
        coVerify(exactly = 1) { fixture.businessLinkDataSource.createLink(fixture.business.id, any()) }
    }

    @Test
    fun `should create the sample services and let the owner provide all of them`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        fixture.sut(fixture.owner.id, "UTC", Language.EN)

        then()
        val sampleServices = DemoBusinessSample.groups.sumOf { it.services.size }
        assertEquals(sampleServices, fixture.createdServices.size)
        coVerify(exactly = DemoBusinessSample.groups.size) {
            fixture.serviceDataSource.createServiceGroup(any<ServiceGroup>())
        }
        assertEquals(fixture.createdServices, fixture.updatedEmployee.captured.services)
        assertEquals(fixture.business.schedule.workingWeek(), fixture.updatedEmployee.captured.schedule)
    }

    @Test
    fun `should price the sample services in the currency of the device language`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        fixture.sut(fixture.owner.id, "UTC", Language.PL)

        then()
        assertEquals(Money.parse("PLN 120.00"), fixture.createdServices.first().price)
        coVerify(exactly = 1) { fixture.businessDataSource.createBusiness(any(), any(), "PLN", any(), true) }
    }

    @Test
    fun `should add the fictional sample clients with example dot com emails`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        fixture.sut(fixture.owner.id, "UTC", Language.EN)

        then()
        val clients = fixture.createdClients.captured
        assertEquals(10, clients.size)
        assertTrue(clients.all { it.email?.endsWith("@example.com") == true }) { clients.toString() }
    }

    @Test
    fun `should announce the seeded business with its ids inside the transaction`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        fixture.sut(fixture.owner.id, "UTC", Language.EN)

        then()
        val event = fixture.sent.captured
        assertEquals(fixture.business.id, event.businessId)
        assertEquals(fixture.owner.id, event.ownerUserId)
        assertEquals(fixture.updatedEmployee.captured.id, event.employeeId)
        assertEquals(fixture.createdServices.map { it.id }, event.serviceIds)
        assertEquals(fixture.createdClients.captured.map { it.id }, event.clientIds)
        assertEquals(listOf(true), fixture.sentInTransaction)
    }

    @Test
    fun `should lock the owner's limits before reading what they own`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        fixture.sut(fixture.owner.id, "UTC", Language.EN)

        then()
        coVerifyOrder {
            fixture.userLimitsDataSource.lockForUser(fixture.owner.id)
            fixture.businessDataSource.countBusinessesOwnedBy(fixture.owner.id)
        }
    }

    @Test
    fun `should seed nothing again when the owner already owns a business`() = runUnitTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.businessDataSource.countBusinessesOwnedBy(fixture.owner.id) } returns 1

        whenn()
        val result = fixture.sut(fixture.owner.id, "UTC", Language.EN)

        then()
        assertTrue(result.isSuccess) { result.toString() }
        coVerify(exactly = 0) { fixture.businessDataSource.createBusiness(any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { fixture.eventProducer.send(any<BusinessEvent.DemoBusinessSeeded>(), any()) }
    }

    @Test
    fun `should fall back to UTC for a time zone it does not know`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        fixture.sut(fixture.owner.id, "Mars/Olympus", Language.EN)

        then()
        coVerify(exactly = 1) { fixture.businessDataSource.createBusiness(any(), any(), any(), TimeZone.UTC, true) }
    }

    @Test
    fun `should seed nothing for a demo user deleted in the meantime`() = runUnitTest {
        given()
        val fixture = SutFixture()
        coEvery { fixture.userClient.getUserById(fixture.owner.id) } returns
            Result.failure(GetUserById.Error.UserNotFound())

        whenn()
        val result = fixture.sut(fixture.owner.id, "UTC", Language.EN)

        then()
        assertTrue(result.isSuccess) { result.toString() }
        coVerify(exactly = 0) { fixture.transactionManager.transaction<Any>(any()) }
    }

    @Test
    fun `should fail so the event is retried when the user service can't be reached`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val error = RuntimeException("user service down")
        coEvery { fixture.userClient.getUserById(fixture.owner.id) } returns Result.failure(error)

        whenn()
        val result = fixture.sut(fixture.owner.id, "UTC", Language.EN)

        then()
        assertSame(error, result.exceptionOrNull())
    }

    @Test
    fun `should seed a regular business when the account was upgraded before the seed arrived`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val upgraded = UserSnapshot.stub(id = fixture.owner.id, email = "ann@example.com")
        coEvery { fixture.userClient.getUserById(fixture.owner.id) } returns Result.success(upgraded)
        coEvery { fixture.businessDataSource.createBusiness(upgraded.id, any(), any(), any(), false) } returns
            fixture.business.copy(demo = false)

        whenn()
        val result = fixture.sut(fixture.owner.id, "UTC", Language.EN)

        then()
        assertTrue(result.isSuccess) { result.toString() }
        coVerify(exactly = 1) { fixture.businessDataSource.createBusiness(any(), any(), any(), any(), false) }
        coVerify(exactly = 0) { fixture.businessLinkDataSource.createLink(any(), any()) }
    }
}
