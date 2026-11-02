package com.bookk.appointments.domain.api.operation

import com.bookk.appointments.domain.api.entity.AppointmentErrorCodes
import com.bookk.appointments.domain.api.entity.AppointmentRequestDraft
import com.bookk.core.domain.entity.BusinessError
import io.ktor.http.HttpStatusCode
import kotlin.uuid.Uuid

interface CreateAppointmentRequest {
    suspend operator fun invoke(userId: Uuid, draft: AppointmentRequestDraft): Result<Unit>

    sealed interface Error {
        class RequestForThisTimeExists : BusinessError(
            statusCode = HttpStatusCode.UnprocessableEntity.value,
            code = AppointmentErrorCodes.REQUEST_EXISTS,
            message = "Request for this time already exists"
        ), Error

        class RequestForThisTimeNotAllowed : BusinessError(
            statusCode = HttpStatusCode.UnprocessableEntity.value,
            code = AppointmentErrorCodes.TIME_NOT_ALLOWED,
            message = "Request for this time not allowed"
        ), Error

        class RequestForThisDateNotAllowed : BusinessError(
            statusCode = HttpStatusCode.UnprocessableEntity.value,
            code = AppointmentErrorCodes.DATE_NOT_ALLOWED,
            message = "Request for this date and time not allowed"
        ), Error

        class DateInThePastNotAllowed : BusinessError(
            statusCode = HttpStatusCode.UnprocessableEntity.value,
            code = AppointmentErrorCodes.DATE_IN_PAST,
            message = "Request date is in the past"
        ), Error
    }
}