package com.bookk.appointments.domain.api.entity

import com.bookk.core.domain.entity.BusinessError
import io.ktor.http.HttpStatusCode

sealed class BookingConflictError(code: Int, message: String) : BusinessError(
    statusCode = HttpStatusCode.UnprocessableEntity.value,
    code = code,
    message = message,
) {
    class EmployeeBusy : BookingConflictError(
        AppointmentErrorCodes.EMPLOYEE_BUSY,
        "The employee already has a booking at this time",
    )
}
