package com.bookk.appointments.domain.api.operation

import com.bookk.appointments.domain.api.entity.AppointmentErrorCodes
import com.bookk.core.domain.entity.BusinessError
import io.ktor.http.HttpStatusCode
import kotlin.uuid.Uuid

interface EnableAppointmentsForBusiness {
    suspend operator fun invoke(userId: Uuid, businessId: Uuid): Result<Unit>

    sealed interface Error {
        class AlreadyEnabled : BusinessError(
            statusCode = HttpStatusCode.UnprocessableEntity.value,
            code = AppointmentErrorCodes.PLUGIN_ALREADY_ENABLED,
            message = "Appointment plugin already enabled"
        ), Error
    }
}
