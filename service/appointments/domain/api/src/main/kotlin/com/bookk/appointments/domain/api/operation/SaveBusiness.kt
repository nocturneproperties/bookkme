package com.bookk.appointments.domain.api.operation

import com.bookk.appointments.domain.api.entity.AppointmentErrorCodes
import com.bookk.core.domain.entity.BusinessError
import io.ktor.http.HttpStatusCode
import kotlin.uuid.Uuid

interface SaveBusiness {
    suspend operator fun invoke(userId: Uuid, businessId: Uuid): Result<Unit>

    sealed interface Error {
        class AppointmentsNotEnabled :
            BusinessError(
                statusCode = HttpStatusCode.UnprocessableEntity.value,
                code = AppointmentErrorCodes.APPOINTMENTS_NOT_ENABLED,
                message = "This business does not take appointments",
            ),
            Error

        class LimitReached :
            BusinessError(
                statusCode = HttpStatusCode.UnprocessableEntity.value,
                code = AppointmentErrorCodes.SAVED_BUSINESSES_LIMIT_REACHED,
                message = "You already saved the maximum number of businesses",
            ),
            Error
    }

    companion object {
        const val MAX_SAVED_BUSINESSES = 100
    }
}
