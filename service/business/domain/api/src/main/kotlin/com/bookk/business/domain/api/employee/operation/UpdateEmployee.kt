package com.bookk.business.domain.api.employee.operation

import com.bookk.business.domain.api.employee.entity.Employee
import com.bookk.business.domain.api.employee.entity.EmployeeUpdateModel
import com.bookk.business.domain.api.error.BusinessErrorCodes
import com.bookk.core.domain.entity.BusinessError
import io.ktor.http.HttpStatusCode
import kotlin.uuid.Uuid

interface UpdateEmployee {
    suspend operator fun invoke(requestUserId: Uuid, employee: EmployeeUpdateModel): Result<Employee>

    sealed interface Error {
        class ValidationError : BusinessError(
            statusCode = HttpStatusCode.UnprocessableEntity.value,
            code = BusinessErrorCodes.BUSINESS_EMPLOYEE_VALIDATION_ERROR,
            message = "Invalid employee name, last name, phone or email"
        ), Error

        class ActiveDayWithoutWorkHours : BusinessError(
            statusCode = HttpStatusCode.UnprocessableEntity.value,
            code = BusinessErrorCodes.BUSINESS_EMPLOYEE_ACTIVE_DAY_WITHOUT_WORK_HOURS,
            message = "Active day must have at least one work hour"
        ), Error

        class InvalidDayOffRange : BusinessError(
            statusCode = HttpStatusCode.UnprocessableEntity.value,
            code = BusinessErrorCodes.BUSINESS_EMPLOYEE_INVALID_DAY_OFF_RANGE,
            message = "Day off range start date must not be after end date"
        ), Error
    }
}
