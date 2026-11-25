package com.bookk.appointments.domain.api.entity

import com.bookk.core.domain.entity.BusinessError
import io.ktor.http.HttpStatusCode

sealed interface HistoryFilterError {
    class DateRangeInvalid :
        BusinessError(
            statusCode = HttpStatusCode.UnprocessableEntity.value,
            code = AppointmentErrorCodes.HISTORY_DATE_RANGE_INVALID,
            message = "The last date is before the first date",
        ),
        HistoryFilterError
}
