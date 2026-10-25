package com.bookk.business.domain.impl.operation.employee

import com.bookk.business.domain.api.business.entity.BusinessResource
import com.bookk.business.domain.api.employee.entity.Employee
import com.bookk.business.domain.datasource.BusinessPermissionDataSource
import com.bookk.business.domain.datasource.EmployeeDataSource
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
import library.permissions.ResourcePermission
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.uuid.Uuid

internal class GetEmployeesImplTest {

    private val userId = Uuid.random()
    private val businessId = Uuid.random()

    private class SutFixture {
        val employeeDataSource = mockk<EmployeeDataSource>()
        val businessPermissionDataSource = mockk<BusinessPermissionDataSource>()
        val transactionManager = mockk<TransactionManager>()
        val sut = GetEmployeesImpl(employeeDataSource, businessPermissionDataSource, transactionManager)

        init {
            coEvery { businessPermissionDataSource.getPermission(any(), any(), BusinessResource.EMPLOYEES) } returns ResourcePermission.FULL
        }

        fun grantPermission(permission: ResourcePermission) {
            coEvery { businessPermissionDataSource.getPermission(any(), any(), BusinessResource.EMPLOYEES) } returns permission
        }
    }

    @Test
    fun `should return employees for the business when caller can view employees`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.transactionManager.mockTransaction()
        val employees = listOf(Employee.stub(businessId = businessId), Employee.stub(businessId = businessId))
        coEvery { fixture.employeeDataSource.getEmployees(businessId) } returns employees

        whenn()
        val result = fixture.sut(userId, businessId)

        then()
        assertTrue(result.isSuccess)
        assertEquals(employees, result.getOrNull())
    }

    @Test
    fun `should return failure when caller has other permissions but cannot view employees`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.transactionManager.mockTransaction()
        fixture.grantPermission(ResourcePermission(view = false, update = true, delete = true))

        whenn()
        val result = fixture.sut(userId, businessId)

        then()
        assertTrue(result.exceptionOrNull() is Error.OperationNotAllowed)
        coVerify(exactly = 0) { fixture.employeeDataSource.getEmployees(any()) }
    }

    @Test
    fun `should return failure when caller has no permission record for the business`() = runUnitTest {
        given()
        val fixture = SutFixture()
        fixture.transactionManager.mockTransaction()
        fixture.grantPermission(ResourcePermission.NONE)

        whenn()
        val result = fixture.sut(userId, businessId)

        then()
        assertTrue(result.exceptionOrNull() is Error.OperationNotAllowed)
        coVerify(exactly = 0) { fixture.employeeDataSource.getEmployees(any()) }
    }
}
