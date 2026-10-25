package com.bookk.business.domain.impl.operation.employee

import com.bookk.business.domain.api.business.entity.BusinessResource
import com.bookk.business.domain.api.employee.entity.Employee
import com.bookk.business.domain.api.employee.entity.EmployeeUpdateModel
import com.bookk.business.domain.api.employee.operation.UpdateEmployee
import com.bookk.business.domain.datasource.BusinessPermissionDataSource
import com.bookk.business.domain.datasource.EmployeeDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.entity.Error
import library.permissions.PermissionAction
import library.permissions.assert
import library.validation.EmailValidator
import library.validation.NameValidator
import library.validation.PhoneValidator
import kotlin.uuid.Uuid

internal class UpdateEmployeeImpl(
    private val employeeDataSource: EmployeeDataSource,
    private val businessPermissionDataSource: BusinessPermissionDataSource,
    private val transactionManager: TransactionManager
) : UpdateEmployee {
    override suspend fun invoke(requestUserId: Uuid, employee: EmployeeUpdateModel): Result<Employee> =
        transactionManager.transaction {
            businessPermissionDataSource.getPermission(requestUserId, employee.businessId, BusinessResource.EMPLOYEES)
                .assert(PermissionAction.UPDATE)
            employeeDataSource.getEmployee(employee.businessId, employee.id) ?: throw Error.NotFound()
            if (!NameValidator.isValid(employee.name) || !NameValidator.isValid(employee.lastName)) {
                throw UpdateEmployee.Error.ValidationError()
            }
            if (employee.phone?.let { !PhoneValidator.isValid(it) } == true) {
                throw UpdateEmployee.Error.ValidationError()
            }
            if (employee.email?.let { !EmailValidator.isValid(it) } == true) {
                throw UpdateEmployee.Error.ValidationError()
            }
            if (employee.schedule.days.values.any { it.isActive && it.workingTime.isEmpty() }) {
                throw UpdateEmployee.Error.ActiveDayWithoutWorkHours()
            }
            if (employee.schedule.dayOffs.any { it.start > it.end }) {
                throw UpdateEmployee.Error.InvalidDayOffRange()
            }
            employeeDataSource.updateEmployee(employee)
        }
}
