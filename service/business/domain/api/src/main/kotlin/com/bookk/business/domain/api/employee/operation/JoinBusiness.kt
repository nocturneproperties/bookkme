package com.bookk.business.domain.api.employee.operation

import com.bookk.business.domain.api.employee.entity.Employee
import com.bookk.business.domain.api.error.BusinessErrorCodes
import com.bookk.core.domain.entity.BusinessError
import io.ktor.http.HttpStatusCode
import kotlin.uuid.Uuid

interface JoinBusiness {
    suspend operator fun invoke(requestUserId: Uuid, code: String): Result<Employee>

    sealed interface Error {
        class EmptyInvitationCode : BusinessError(
            statusCode = HttpStatusCode.UnprocessableEntity.value,
            code = BusinessErrorCodes.BUSINESS_EMPLOYEE_INVITATION_CODE_EMPTY,
            message = "Invitation code must not be empty"
        ), Error

        class InvitationAlreadyProcessed : BusinessError(
            statusCode = HttpStatusCode.UnprocessableEntity.value,
            code = BusinessErrorCodes.BUSINESS_EMPLOYEE_INVITATION_ALREADY_PROCESSED,
            message = "Invitation is already processed"
        ), Error

        class EmployeeExist : BusinessError(
            statusCode = HttpStatusCode.UnprocessableEntity.value,
            code = BusinessErrorCodes.BUSINESS_EMPLOYEE_EXISTS,
            message = "User is already an employee of this business"
        ), Error
    }
}
