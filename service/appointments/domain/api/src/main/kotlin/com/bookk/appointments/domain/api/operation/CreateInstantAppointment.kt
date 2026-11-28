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
        class DateInThePastNotAllowed :
            BusinessError(
                statusCode = HttpStatusCode.UnprocessableEntity.value,
                code = AppointmentErrorCodes.DATE_IN_PAST,
                message = "Appointment date is in the past",
            ),
            Error
    }
}
