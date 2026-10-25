package com.bookk.appointments.domain.api.entity

import com.bookk.core.domain.entity.BusinessError
import io.ktor.http.HttpStatusCode

sealed class AppointmentRequestStatusError(code: Int, message: String) : BusinessError(
    statusCode = HttpStatusCode.UnprocessableEntity.value,
    code = code,
    message = message
) {
    class AlreadyDeclined : AppointmentRequestStatusError(AppointmentErrorCodes.REQUEST_ALREADY_DECLINED, "Appointment request already declined")

    class AlreadyApproved : AppointmentRequestStatusError(AppointmentErrorCodes.REQUEST_ALREADY_APPROVED, "Appointment request already approved")
}
