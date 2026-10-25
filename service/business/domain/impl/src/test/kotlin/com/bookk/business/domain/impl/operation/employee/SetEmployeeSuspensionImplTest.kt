package com.bookk.business.domain.impl.operation.employee

import com.bookk.business.domain.api.business.entity.BusinessPermissions
import com.bookk.business.domain.api.employee.entity.Employee
import com.bookk.business.domain.api.employee.operation.SetEmployeeSuspension
import com.bookk.business.domain.datasource.BusinessDataSource
import com.bookk.business.domain.datasource.EmployeeDataSource
import com.bookk.core.data.eventstreaming.StandardEventProducer
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.mockTransaction
import com.bookk.core.domain.entity.Error
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import com.bookk.server.business.client.api.event.BusinessEvent
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class SetEmployeeSuspensionImplTest {

    private val requestUserId = Uuid.random()
    private val businessId = Uuid.random()

    private class SutFixture(requestUserId: Uuid, businessId: Uuid) {
        val employeeDataSource = mockk<EmployeeDataSource>()
        val businessDataSource = mockk<BusinessDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val eventProducer = mockk<StandardEventProducer>(relaxed = true)
        val sut = SetEmployeeSuspensionImpl(employeeDataSource, businessDataSource, transactionManager, eventProducer)

        init {
            coEvery { businessDataSource.isOwner(any(), any()) } returns false
            coEvery { businessDataSource.isOwner(requestUserId, businessId) } returns true
        }
    }

    @Test
    fun `should suspend an active employee`() = runUnitTest {
        given()
        val fixture = SutFixture(requestUserId, businessId)
        val employee = Employee.stub(businessId = businessId)
        val suspended = employee.copy(suspendedAt = Instant.fromEpochMilliseconds(1))
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { employeeDataSource.getEmployee(businessId, employee.id) } returns employee
            coEvery { employeeDataSource.setSuspendedAt(employee.id, any()) } returns suspended
        }

        whenn()
        val result = fixture.sut(requestUserId, businessId, employee.id, suspended = true)

        then()
        assertEquals(suspended, result.getOrNull())
        coVerify(exactly = 1) { fixture.employeeDataSource.setSuspendedAt(employee.id, match { it != null }) }
    }

    @Test
    fun `should publish the stored permissions flagged as suspended when the employee is suspended`() = runUnitTest {
        given()
        val fixture = SutFixture(requestUserId, businessId)
        val employee = Employee.stub(businessId = businessId, permissions = BusinessPermissions.FULL)
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { employeeDataSource.getEmployee(businessId, employee.id) } returns employee
            coEvery { employeeDataSource.setSuspendedAt(employee.id, any()) } returns employee.copy(suspendedAt = Instant.fromEpochMilliseconds(1))
        }

        whenn()
        fixture.sut(requestUserId, businessId, employee.id, suspended = true)

        then()
        coVerify(exactly = 1) {
            fixture.eventProducer.send(
                match<BusinessEvent.EmployeePermissionsChanged> {
                    it.employeeUserId == employee.userId && it.businessId == businessId && it.permissions == BusinessPermissions.FULL && it.suspended
                },
                any()
            )
        }
    }

    @Test
    fun `should reinstate a suspended employee keeping the stored permissions`() = runUnitTest {
        given()
        val fixture = SutFixture(requestUserId, businessId)
        val employee = Employee.stub(businessId = businessId, permissions = BusinessPermissions.VIEW_ONLY, suspendedAt = Instant.fromEpochMilliseconds(1))
        val reinstated = employee.copy(suspendedAt = null)
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { employeeDataSource.getEmployee(businessId, employee.id) } returns employee
            coEvery { employeeDataSource.setSuspendedAt(employee.id, null) } returns reinstated
        }

        whenn()
        val result = fixture.sut(requestUserId, businessId, employee.id, suspended = false)

        then()
        assertEquals(reinstated, result.getOrNull())
        coVerify(exactly = 1) { fixture.employeeDataSource.setSuspendedAt(employee.id, null) }
    }

    @Test
    fun `should publish the stored permissions when the employee is reinstated`() = runUnitTest {
        given()
        val fixture = SutFixture(requestUserId, businessId)
        val employee = Employee.stub(businessId = businessId, permissions = BusinessPermissions.VIEW_ONLY, suspendedAt = Instant.fromEpochMilliseconds(1))
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { employeeDataSource.getEmployee(businessId, employee.id) } returns employee
            coEvery { employeeDataSource.setSuspendedAt(employee.id, null) } returns employee.copy(suspendedAt = null)
        }

        whenn()
        fixture.sut(requestUserId, businessId, employee.id, suspended = false)

        then()
        coVerify(exactly = 1) {
            fixture.eventProducer.send(
                match<BusinessEvent.EmployeePermissionsChanged> {
                    it.employeeUserId == employee.userId && it.permissions == BusinessPermissions.VIEW_ONLY && !it.suspended
                },
                any()
            )
        }
    }

    @Test
    fun `should return the employee unchanged without writing or publishing when already suspended`() = runUnitTest {
        given()
        val fixture = SutFixture(requestUserId, businessId)
        val employee = Employee.stub(businessId = businessId, suspendedAt = Instant.fromEpochMilliseconds(1))
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { employeeDataSource.getEmployee(businessId, employee.id) } returns employee
        }

        whenn()
        val result = fixture.sut(requestUserId, businessId, employee.id, suspended = true)

        then()
        assertEquals(employee, result.getOrNull())
        coVerify(exactly = 0) { fixture.employeeDataSource.setSuspendedAt(any(), any()) }
        coVerify(exactly = 0) { fixture.eventProducer.send(any(BusinessEvent.EmployeePermissionsChanged::class), any()) }
    }

    @Test
    fun `should return the employee unchanged without writing or publishing when already active`() = runUnitTest {
        given()
        val fixture = SutFixture(requestUserId, businessId)
        val employee = Employee.stub(businessId = businessId)
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { employeeDataSource.getEmployee(businessId, employee.id) } returns employee
        }

        whenn()
        val result = fixture.sut(requestUserId, businessId, employee.id, suspended = false)

        then()
        assertEquals(employee, result.getOrNull())
        coVerify(exactly = 0) { fixture.employeeDataSource.setSuspendedAt(any(), any()) }
        coVerify(exactly = 0) { fixture.eventProducer.send(any(BusinessEvent.EmployeePermissionsChanged::class), any()) }
    }

    @Test
    fun `should reject suspending the business owner`() = runUnitTest {
        given()
        val fixture = SutFixture(requestUserId, businessId)
        val owner = Employee.stub(businessId = businessId, userId = requestUserId, permissions = BusinessPermissions.FULL)
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { employeeDataSource.getEmployee(businessId, owner.id) } returns owner
        }

        whenn()
        val result = fixture.sut(requestUserId, businessId, owner.id, suspended = true)

        then()
        assertTrue(result.exceptionOrNull() is SetEmployeeSuspension.Error.OwnerSuspensionNotAllowed)
        coVerify(exactly = 0) { fixture.employeeDataSource.setSuspendedAt(any(), any()) }
        coVerify(exactly = 0) { fixture.eventProducer.send(any(BusinessEvent.EmployeePermissionsChanged::class), any()) }
    }

    @Test
    fun `should return failure when employee does not exist`() = runUnitTest {
        given()
        val fixture = SutFixture(requestUserId, businessId)
        val employeeId = Uuid.random()
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { employeeDataSource.getEmployee(businessId, employeeId) } returns null
        }

        whenn()
        val result = fixture.sut(requestUserId, businessId, employeeId, suspended = true)

        then()
        assertTrue(result.exceptionOrNull() is Error.NotFound)
    }

    @Test
    fun `should return failure when caller is not the business owner`() = runUnitTest {
        given()
        val fixture = SutFixture(requestUserId, businessId)
        val employee = Employee.stub(businessId = businessId)
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { businessDataSource.isOwner(requestUserId, businessId) } returns false
        }

        whenn()
        val result = fixture.sut(requestUserId, businessId, employee.id, suspended = true)

        then()
        assertTrue(result.exceptionOrNull() is Error.OperationNotAllowed)
        coVerify(exactly = 0) { fixture.employeeDataSource.getEmployee(any(), any()) }
        coVerify(exactly = 0) { fixture.eventProducer.send(any(BusinessEvent.EmployeePermissionsChanged::class), any()) }
    }
}
