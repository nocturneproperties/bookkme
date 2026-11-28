package com.bookk.appointments.domain.api.operation

import com.bookk.appointments.domain.api.entity.AppointmentErrorCodes
import com.bookk.appointments.domain.api.entity.DaySlots
import com.bookk.appointments.domain.api.entity.RequestedService
import com.bookk.core.domain.entity.BusinessError
import io.ktor.http.HttpStatusCode
import kotlinx.datetime.LocalDate
import kotlin.uuid.Uuid

interface GetAvailableSlots {
    suspend operator fun invoke(
        businessId: Uuid,
        employeeId: Uuid,
        services: List<RequestedService>,
        from: LocalDate,
        to: LocalDate,
    ): Result<List<DaySlots>>

    sealed interface Error {
        class InvalidServiceSelection :
            BusinessError(
                statusCode = HttpStatusCode.UnprocessableEntity.value,
                code = AppointmentErrorCodes.SERVICE_SELECTION_INVALID,
                message = "Services must be a non-empty list of distinct services with positive counts, " +
                    "at most $MAX_SERVICES in total",
            ),
            Error

        class InvalidRange :
            BusinessError(
                statusCode = HttpStatusCode.UnprocessableEntity.value,
                code = AppointmentErrorCodes.SLOT_RANGE_INVALID,
                message = "The date range must not end before it starts and must span at most $MAX_DAYS days",
            ),
            Error
    }

    companion object {
        const val MAX_DAYS = 31
        const val MAX_SERVICES = 100
        const val STEP_MINUTES = 15
    }
}
