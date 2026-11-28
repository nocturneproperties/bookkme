package com.bookk.appointments.domain.api.operation

import com.bookk.appointments.domain.api.entity.Appointment
import com.bookk.appointments.domain.api.entity.AppointmentErrorCodes
import com.bookk.appointments.domain.api.entity.AppointmentUpdate
import com.bookk.core.domain.entity.BusinessError
import io.ktor.http.HttpStatusCode
import kotlin.uuid.Uuid

interface UpdateAppointment {
    suspend operator fun invoke(userId: Uuid, update: AppointmentUpdate): Result<Appointment>

    sealed interface Error {
        class DateInThePastNotAllowed :
            BusinessError(
                statusCode = HttpStatusCode.UnprocessableEntity.value,
                code = AppointmentErrorCodes.DATE_IN_PAST,
                message = "Appointment date is in the past",
            ),
            Error

        class InvalidServiceSelection :
            BusinessError(
                statusCode = HttpStatusCode.UnprocessableEntity.value,
                code = AppointmentErrorCodes.SERVICE_SELECTION_INVALID,
                message = "Services must be a non-empty list of distinct services with positive counts",
            ),
            Error
    }
}
