package com.bookk.appointments.domain.api.entity

import com.bookk.core.domain.entity.BusinessError
import io.ktor.http.HttpStatusCode

sealed class AppointmentStatusError(code: Int, message: String) : BusinessError(
    statusCode = HttpStatusCode.UnprocessableEntity.value,
    code = code,
    message = message
) {
    class AlreadyCancelled : AppointmentStatusError(AppointmentErrorCodes.APPOINTMENT_ALREADY_CANCELED, "Appointment already cancelled")

    class AlreadyCompleted : AppointmentStatusError(AppointmentErrorCodes.APPOINTMENT_ALREADY_COMPLETED, "Appointment already completed")

    class MarkedNoShow : AppointmentStatusError(AppointmentErrorCodes.APPOINTMENT_MARKED_NO_SHOW, "Appointment is marked as no-show")

    class NotStarted : AppointmentStatusError(AppointmentErrorCodes.APPOINTMENT_NOT_STARTED, "Appointment has not started yet")
}
