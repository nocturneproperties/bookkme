package com.bookk.appointments.domain.api.operation

import com.bookk.appointments.domain.api.entity.Appointment
import com.bookk.appointments.domain.api.entity.AppointmentErrorCodes
import com.bookk.appointments.domain.api.entity.AppointmentRequest
import com.bookk.core.domain.entity.BusinessError
import io.ktor.http.HttpStatusCode
import library.schedule.Schedule
import kotlin.uuid.Uuid

interface CreateAppointment {
    suspend operator fun invoke(userId: Uuid, appointmentRequestId: Uuid): Result<Appointment>
    suspend fun createBypassingPermissions(request: AppointmentRequest, employeeSchedule: Schedule): Result<Appointment>

    sealed interface Error {
        class AppointmentForThisTimeExists :
            BusinessError(
                statusCode = HttpStatusCode.UnprocessableEntity.value,
                code = AppointmentErrorCodes.APPOINTMENT_EXISTS,
                message = "Appointment for this time already exists",
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
