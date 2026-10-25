package com.bookk.business.domain.api.employee.operation

import com.bookk.business.domain.api.employee.entity.EmployeeInvitation
import com.bookk.business.domain.api.error.BusinessErrorCodes
import com.bookk.core.domain.entity.BusinessError
import io.ktor.http.HttpStatusCode
import kotlin.uuid.Uuid

interface CreateEmployeeInvitation {
    suspend operator fun invoke(requestUserId: Uuid, businessId: Uuid): Result<EmployeeInvitation>

    sealed interface Error {
        class PendingInvitationsLimitReached : BusinessError(
            statusCode = HttpStatusCode.UnprocessableEntity.value,
            code = BusinessErrorCodes.BUSINESS_EMPLOYEE_PENDING_INVITATIONS_LIMIT_REACHED,
            message = "Business already has the maximum number of pending invitations"
        ), Error

        class DailyInvitationsLimitReached : BusinessError(
            statusCode = HttpStatusCode.UnprocessableEntity.value,
            code = BusinessErrorCodes.BUSINESS_EMPLOYEE_DAILY_INVITATIONS_LIMIT_REACHED,
            message = "Business already created the maximum number of invitations in the last 24 hours"
        ), Error
    }

    companion object {
        const val MAX_PENDING_INVITATIONS = 20
        const val MAX_INVITATIONS_PER_DAY = 50
    }
}
