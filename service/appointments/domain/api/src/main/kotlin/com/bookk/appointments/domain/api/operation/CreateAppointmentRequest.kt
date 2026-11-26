package com.bookk.appointments.domain.api.operation

import com.bookk.appointments.domain.api.entity.AppointmentErrorCodes
import com.bookk.appointments.domain.api.entity.AppointmentRequestDraft
import com.bookk.core.domain.entity.BusinessError
import io.ktor.http.HttpStatusCode
import kotlin.uuid.Uuid

interface CreateAppointmentRequest {
    suspend operator fun invoke(userId: Uuid, draft: AppointmentRequestDraft, isDemo: Boolean): Result<Unit>

    sealed interface Error {
        class DateInThePastNotAllowed :
            BusinessError(
                statusCode = HttpStatusCode.UnprocessableEntity.value,
                code = AppointmentErrorCodes.DATE_IN_PAST,
                message = "Request date is in the past",
            ),
            Error

        class PendingRequestsLimitReached :
            BusinessError(
                statusCode = HttpStatusCode.UnprocessableEntity.value,
                code = AppointmentErrorCodes.PENDING_REQUESTS_LIMIT_REACHED,
                message = "You already have the maximum number of pending requests at this business",
            ),
            Error

        class DemoRestricted :
            BusinessError(
                statusCode = HttpStatusCode.UnprocessableEntity.value,
                code = AppointmentErrorCodes.APPOINTMENTS_DEMO_RESTRICTED,
                message = "A demo account can't book appointments, upgrade the account first",
            ),
            Error
    }
}
