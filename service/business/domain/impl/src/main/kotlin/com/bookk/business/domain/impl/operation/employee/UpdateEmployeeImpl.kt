package com.bookk.business.domain.impl.operation.employee

import com.bookk.business.domain.api.business.entity.BusinessResource
import com.bookk.business.domain.api.employee.entity.Employee
import com.bookk.business.domain.api.employee.entity.EmployeeUpdateModel
import com.bookk.business.domain.api.employee.operation.UpdateEmployee
import com.bookk.business.domain.datasource.BusinessPermissionDataSource
import com.bookk.business.domain.datasource.EmployeeDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.entity.BusinessError
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
    private val transactionManager: TransactionManager,
) : UpdateEmployee {
    override suspend fun invoke(requestUserId: Uuid, employee: EmployeeUpdateModel): Result<Employee> {
        val inputError = employee.inputError()
        if (inputError != null) return Result.failure(inputError)
        return transactionManager.transaction {
            businessPermissionDataSource.getPermission(requestUserId, employee.businessId, BusinessResource.EMPLOYEES)
                .assert(PermissionAction.UPDATE)
            employeeDataSource.getEmployee(employee.businessId, employee.id) ?: throw Error.NotFound()
            employeeDataSource.updateEmployee(employee)
        }
    }

    private fun EmployeeUpdateModel.inputError(): BusinessError? {
        val hasInvalidName = !NameValidator.isValid(name) || !NameValidator.isValid(lastName, minLength = 0)
        val hasInvalidPhone = phone?.let { !PhoneValidator.isValid(it) } == true
        val hasInvalidEmail = email?.let { !EmailValidator.isValid(it) } == true
        return when {
            hasInvalidName || hasInvalidPhone || hasInvalidEmail -> UpdateEmployee.Error.ValidationError()

            schedule.days.values.any { it.isActive && it.workingTime.isEmpty() } ->
                UpdateEmployee.Error.ActiveDayWithoutWorkHours()

            schedule.hasEmptyWorkHour() -> UpdateEmployee.Error.EmptyWorkHourRange()

            schedule.dayOffs.any { it.start > it.end } -> UpdateEmployee.Error.InvalidDayOffRange()

            else -> null
        }
    }
}
