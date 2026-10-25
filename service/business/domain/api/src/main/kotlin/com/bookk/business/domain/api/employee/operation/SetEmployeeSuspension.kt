package com.bookk.business.domain.api.employee.operation

import com.bookk.business.domain.api.employee.entity.Employee
import com.bookk.business.domain.api.error.BusinessErrorCodes
import com.bookk.core.domain.entity.BusinessError
import io.ktor.http.HttpStatusCode
import kotlin.uuid.Uuid

interface SetEmployeeSuspension {
    suspend operator fun invoke(
        requestUserId: Uuid,
        businessId: Uuid,
        employeeId: Uuid,
        suspended: Boolean
    ): Result<Employee>

    sealed interface Error {
        class OwnerSuspensionNotAllowed : BusinessError(
            statusCode = HttpStatusCode.UnprocessableEntity.value,
            code = BusinessErrorCodes.BUSINESS_OWNER_SUSPENSION_NOT_ALLOWED,
            message = "Business owner cannot be suspended"
        ), Error
    }
}
