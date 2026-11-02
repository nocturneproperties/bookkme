package com.bookk.appointments.domain.api.entity

import com.bookk.core.domain.entity.BusinessError
import io.ktor.http.HttpStatusCode

sealed class BookingQuoteError(statusCode: HttpStatusCode, code: Int, message: String) : BusinessError(
    statusCode = statusCode.value,
    code = code,
    message = message
) {
    class PriceChanged : BookingQuoteError(
        HttpStatusCode.BadRequest,
        AppointmentErrorCodes.PRICE_CHANGED,
        "While booking, price for services has been changed, refresh"
    )

    class DurationChanged : BookingQuoteError(
        HttpStatusCode.BadRequest,
        AppointmentErrorCodes.DURATION_CHANGED,
        "While booking, duration for services has been changed, refresh"
    )

    class ServicesSignatureMiss : BookingQuoteError(
        HttpStatusCode.BadRequest,
        AppointmentErrorCodes.SERVICES_VALIDATION_FAILED,
        "Quote token invalid or requested services does not match with the quote token"
    )

    class TokenAlreadyUsed : BookingQuoteError(
        HttpStatusCode.UnprocessableEntity,
        AppointmentErrorCodes.QUOTE_TOKEN_ALREADY_USED,
        "Quote token invalid or requested services does not match with the quote token"
    )
}
