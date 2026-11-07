package com.bookk.appointments.domain.api.operation

import com.bookk.appointments.domain.api.entity.Appointment
import com.bookk.appointments.domain.api.entity.AppointmentErrorCodes
import com.bookk.appointments.domain.api.entity.InstantAppointmentDraft
import com.bookk.core.domain.entity.BusinessError
import io.ktor.http.HttpStatusCode
import kotlin.uuid.Uuid

interface CreateInstantAppointment {
    suspend operator fun invoke(userId: Uuid, draft: InstantAppointmentDraft): Result<Appointment>

    sealed interface Error {
        class AppointmentForThisTimeExists :
            BusinessError(
                statusCode = HttpStatusCode.UnprocessableEntity.value,
                code = AppointmentErrorCodes.APPOINTMENT_EXISTS,
                message = "Appointment for this time already exists",
            ),
            Error

        class RequestForThisTimeNotAllowed :
            BusinessError(
                statusCode = HttpStatusCode.UnprocessableEntity.value,
                code = AppointmentErrorCodes.TIME_NOT_ALLOWED,
                message = "Request for this time not allowed",
            ),
            Error

        class RequestForThisDateNotAllowed :
            BusinessError(
                statusCode = HttpStatusCode.UnprocessableEntity.value,
                code = AppointmentErrorCodes.DATE_NOT_ALLOWED,
                message = "Request for this date and time not allowed",
            ),
            Error

        class DateInThePastNotAllowed :
            BusinessError(
                statusCode = HttpStatusCode.UnprocessableEntity.value,
                code = AppointmentErrorCodes.DATE_IN_PAST,
                message = "Appointment date is in the past",
            ),
            Error
    }
}
