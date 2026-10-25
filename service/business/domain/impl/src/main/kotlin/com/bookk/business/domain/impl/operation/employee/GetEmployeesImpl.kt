package com.bookk.business.domain.impl.operation.employee

import com.bookk.business.domain.api.business.entity.BusinessResource
import com.bookk.business.domain.api.employee.entity.Employee
import com.bookk.business.domain.api.employee.operation.GetEmployees
import com.bookk.business.domain.datasource.BusinessPermissionDataSource
import com.bookk.business.domain.datasource.EmployeeDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import library.permissions.PermissionAction
import library.permissions.assert
import kotlin.uuid.Uuid

internal class GetEmployeesImpl(
    private val employeeDataSource: EmployeeDataSource,
    private val businessPermissionDataSource: BusinessPermissionDataSource,
    private val transactionManager: TransactionManager
) : GetEmployees {
    override suspend fun invoke(userId: Uuid, businessId: Uuid): Result<List<Employee>> = transactionManager.transaction {
        businessPermissionDataSource.getPermission(userId, businessId, BusinessResource.EMPLOYEES).assert(PermissionAction.VIEW)
        employeeDataSource.getEmployees(businessId)
    }
}
