package com.bookk.appointments.domain.api.operation

import com.bookk.appointments.domain.api.entity.Appointment
import com.bookk.appointments.domain.api.entity.AppointmentErrorCodes
import com.bookk.appointments.domain.api.entity.PriceAdjustmentDraft
import com.bookk.core.domain.entity.BusinessError
import io.ktor.http.HttpStatusCode
import kotlin.uuid.Uuid

interface CompleteAppointment {
    suspend operator fun invoke(userId: Uuid, appointmentId: Uuid, priceAdjustment: PriceAdjustmentDraft?): Result<Appointment>

    sealed interface Error {
        class NegativePrice : Error, BusinessError(
            statusCode = HttpStatusCode.UnprocessableEntity.value,
            code = AppointmentErrorCodes.PRICE_ADJUSTMENT_NEGATIVE_PRICE,
            message = "Adjusted price must not be negative",
        )

        class CurrencyMismatch : Error, BusinessError(
            statusCode = HttpStatusCode.UnprocessableEntity.value,
            code = AppointmentErrorCodes.PRICE_ADJUSTMENT_CURRENCY_MISMATCH,
            message = "Adjusted price currency must match the appointment currency",
        )

        class ReasonTooLong : Error, BusinessError(
            statusCode = HttpStatusCode.UnprocessableEntity.value,
            code = AppointmentErrorCodes.PRICE_ADJUSTMENT_REASON_TOO_LONG,
            message = "Price adjustment reason is too long",
        )
    }
}
