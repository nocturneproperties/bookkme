package com.bookk.business.domain.impl.operation.employee

import com.bookk.business.domain.api.business.entity.BusinessPermissions
import com.bookk.business.domain.api.business.entity.BusinessResource
import com.bookk.business.domain.api.employee.entity.Employee
import com.bookk.business.domain.api.employee.operation.SetEmployeePermissions
import com.bookk.business.domain.datasource.BusinessDataSource
import com.bookk.business.domain.datasource.BusinessPermissionDataSource
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
import library.permissions.ResourcePermission
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class SetEmployeePermissionsImplTest {

    private val requestUserId = Uuid.random()
    private val businessId = Uuid.random()

    private class SutFixture(requestUserId: Uuid, businessId: Uuid) {
        val employeeDataSource = mockk<EmployeeDataSource>()
        val businessDataSource = mockk<BusinessDataSource>()
        val businessPermissionDataSource = mockk<BusinessPermissionDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val eventProducer = mockk<StandardEventProducer>(relaxed = true)
        val sut = SetEmployeePermissionsImpl(employeeDataSource, businessDataSource, businessPermissionDataSource, transactionManager, eventProducer)

        init {
            coEvery { businessDataSource.isOwner(any(), any()) } returns false
            coEvery { businessDataSource.isOwner(requestUserId, businessId) } returns true
        }
    }

    @Test
    fun `should grant every requested resource and return the employee with merged permissions`() = runUnitTest {
        given()
        val fixture = SutFixture(requestUserId, businessId)
        val employee = Employee.stub(
            businessId = businessId,
            permissions = BusinessPermissions.stub(appointments = ResourcePermission(view = true, update = false, delete = false))
        )
        val grants = mapOf(
            BusinessResource.CLIENTS to ResourcePermission.FULL,
            BusinessResource.SERVICES to ResourcePermission(view = true, update = false, delete = false)
        )
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { employeeDataSource.getEmployee(businessId, employee.id) } returns employee
            coEvery { businessPermissionDataSource.setPermissions(employee, grants) } returns Unit
        }

        whenn()
        val result = fixture.sut(requestUserId, businessId, employee.id, grants)

        then()
        assertEquals(
            employee.copy(
                permissions = BusinessPermissions.stub(
                    appointments = ResourcePermission(view = true, update = false, delete = false),
                    clients = ResourcePermission.FULL,
                    services = ResourcePermission(view = true, update = false, delete = false)
                )
            ),
            result.getOrNull()
        )
        coVerify(exactly = 1) { fixture.businessPermissionDataSource.setPermissions(employee, grants) }
    }

    @Test
    fun `should publish a single employee permissions changed event with the merged permissions`() = runUnitTest {
        given()
        val fixture = SutFixture(requestUserId, businessId)
        val employee = Employee.stub(businessId = businessId)
        val grants = mapOf(
            BusinessResource.CLIENTS to ResourcePermission(view = true, update = false, delete = false),
            BusinessResource.APPOINTMENTS to ResourcePermission(view = true, update = true, delete = false)
        )
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { employeeDataSource.getEmployee(businessId, employee.id) } returns employee
            coEvery { businessPermissionDataSource.setPermissions(any(), any()) } returns Unit
        }
        val expected = BusinessPermissions.stub(
            clients = ResourcePermission(view = true, update = false, delete = false),
            appointments = ResourcePermission(view = true, update = true, delete = false)
        )

        whenn()
        val result = fixture.sut(requestUserId, businessId, employee.id, grants)

        then()
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) {
            fixture.eventProducer.send(
                match<BusinessEvent.EmployeePermissionsChanged> {
                    it.employeeUserId == employee.userId && it.businessId == businessId && it.permissions == expected && !it.suspended
                },
                any()
            )
        }
    }

    @Test
    fun `should publish the updated grants flagged as suspended while the employee is suspended`() = runUnitTest {
        given()
        val fixture = SutFixture(requestUserId, businessId)
        val employee = Employee.stub(businessId = businessId, suspendedAt = Instant.fromEpochMilliseconds(1))
        val grants = mapOf(BusinessResource.APPOINTMENTS to ResourcePermission.FULL)
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { employeeDataSource.getEmployee(businessId, employee.id) } returns employee
            coEvery { businessPermissionDataSource.setPermissions(employee, grants) } returns Unit
        }

        whenn()
        val result = fixture.sut(requestUserId, businessId, employee.id, grants)

        then()
        assertEquals(BusinessPermissions.stub(appointments = ResourcePermission.FULL), result.getOrNull()?.permissions)
        coVerify(exactly = 1) { fixture.businessPermissionDataSource.setPermissions(employee, grants) }
        coVerify(exactly = 1) {
            fixture.eventProducer.send(
                match<BusinessEvent.EmployeePermissionsChanged> {
                    it.permissions == BusinessPermissions.stub(appointments = ResourcePermission.FULL) && it.suspended
                },
                any()
            )
        }
    }

    @Test
    fun `should return the employee unchanged without writing or publishing when no grants are requested`() = runUnitTest {
        given()
        val fixture = SutFixture(requestUserId, businessId)
        val employee = Employee.stub(businessId = businessId, permissions = BusinessPermissions.VIEW_ONLY)
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { employeeDataSource.getEmployee(businessId, employee.id) } returns employee
        }

        whenn()
        val result = fixture.sut(requestUserId, businessId, employee.id, emptyMap())

        then()
        assertEquals(employee, result.getOrNull())
        coVerify(exactly = 0) { fixture.businessPermissionDataSource.setPermissions(any(), any()) }
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
        val result = fixture.sut(requestUserId, businessId, employeeId, mapOf(BusinessResource.CLIENTS to ResourcePermission.FULL))

        then()
        assertTrue(result.exceptionOrNull() is Error.NotFound)
    }

    @Test
    fun `should reject changing permissions of the business owner`() = runUnitTest {
        given()
        val fixture = SutFixture(requestUserId, businessId)
        val owner = Employee.stub(businessId = businessId, userId = requestUserId, permissions = BusinessPermissions.FULL)
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { employeeDataSource.getEmployee(businessId, owner.id) } returns owner
        }

        whenn()
        val result = fixture.sut(requestUserId, businessId, owner.id, mapOf(BusinessResource.CLIENTS to ResourcePermission.NONE))

        then()
        assertTrue(result.exceptionOrNull() is SetEmployeePermissions.Error.OwnerPermissionsImmutable)
        coVerify(exactly = 0) { fixture.businessPermissionDataSource.setPermissions(any(), any()) }
        coVerify(exactly = 0) { fixture.eventProducer.send(any(BusinessEvent.EmployeePermissionsChanged::class), any()) }
    }

    @Test
    fun `should return failure when caller is not the business owner even with full employee permissions`() = runUnitTest {
        given()
        val fixture = SutFixture(requestUserId, businessId)
        val employee = Employee.stub(businessId = businessId)
        with(fixture) {
            transactionManager.mockTransaction()
            coEvery { businessDataSource.isOwner(requestUserId, businessId) } returns false
            coEvery { businessPermissionDataSource.getPermission(requestUserId, businessId, any()) } returns ResourcePermission.FULL
        }

        whenn()
        val result = fixture.sut(requestUserId, businessId, employee.id, mapOf(BusinessResource.CLIENTS to ResourcePermission.FULL))

        then()
        assertTrue(result.exceptionOrNull() is Error.OperationNotAllowed)
        coVerify(exactly = 0) { fixture.employeeDataSource.getEmployee(any(), any()) }
        coVerify(exactly = 0) { fixture.businessPermissionDataSource.setPermissions(any(), any()) }
        coVerify(exactly = 0) { fixture.eventProducer.send(any(BusinessEvent.EmployeePermissionsChanged::class), any()) }
    }
}
