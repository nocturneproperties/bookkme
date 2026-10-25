package com.bookk.business.domain.api.employee.operation

import com.bookk.business.domain.api.error.BusinessErrorCodes
import com.bookk.core.domain.entity.BusinessError
import io.ktor.http.HttpStatusCode
import kotlin.uuid.Uuid

interface RevokeEmployeeInvitation {
    suspend operator fun invoke(requestUserId: Uuid, businessId: Uuid, id: Uuid): Result<Unit>

    sealed interface Error {
        class InvitationAlreadyProcessed : BusinessError(
            statusCode = HttpStatusCode.UnprocessableEntity.value,
            code = BusinessErrorCodes.BUSINESS_EMPLOYEE_INVITATION_ALREADY_PROCESSED,
            message = "Invitation is already processed"
        ), Error
    }
}
