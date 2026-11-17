package com.bookk.appointments.domain.api.entity

import com.bookk.core.domain.entity.BusinessError
import io.ktor.http.HttpStatusCode

sealed class BookingScheduleError(code: Int, message: String) : BusinessError(
    statusCode = HttpStatusCode.UnprocessableEntity.value,
    code = code,
    message = message,
) {
    class BusinessDateNotAllowed : BookingScheduleError(
        AppointmentErrorCodes.BUSINESS_DATE_NOT_ALLOWED,
        "The business does not work on this date",
    )

    class BusinessTimeNotAllowed : BookingScheduleError(
        AppointmentErrorCodes.BUSINESS_TIME_NOT_ALLOWED,
        "This time is outside the business working hours",
    )

    class EmployeeDateNotAllowed : BookingScheduleError(
        AppointmentErrorCodes.EMPLOYEE_DATE_NOT_ALLOWED,
        "The employee does not work on this date",
    )

    class EmployeeTimeNotAllowed : BookingScheduleError(
        AppointmentErrorCodes.EMPLOYEE_TIME_NOT_ALLOWED,
        "This time is outside the employee working hours",
    )
}
