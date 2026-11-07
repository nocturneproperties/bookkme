package com.bookk.appointments.domain.api.entity

import com.bookk.core.domain.entity.BusinessError
import io.ktor.http.HttpStatusCode

sealed class AppointmentStatusError(code: Int, message: String) : BusinessError(
    statusCode = HttpStatusCode.UnprocessableEntity.value,
    code = code,
    message = message,
) {
    class AlreadyCancelled : AppointmentStatusError(AppointmentErrorCodes.APPOINTMENT_ALREADY_CANCELED, "Appointment already cancelled")

    class NotStarted : AppointmentStatusError(AppointmentErrorCodes.APPOINTMENT_NOT_STARTED, "Appointment has not started yet")

    class EditWindowClosed : AppointmentStatusError(
        AppointmentErrorCodes.APPOINTMENT_EDIT_WINDOW_CLOSED,
        "Appointment ended more than ${Appointment.EDIT_WINDOW.inWholeDays} days ago and can no longer be changed",
    )
}
