package com.bookk.appointments.domain.api.operation

import com.bookk.appointments.domain.api.entity.AppointmentRequestPage
import com.bookk.appointments.domain.api.entity.OwnAppointmentRequestFilter
import kotlin.uuid.Uuid

interface GetOwnAppointmentRequestHistory {
    suspend operator fun invoke(
        userId: Uuid,
        filter: OwnAppointmentRequestFilter,
        limit: Int?,
        cursor: String?,
    ): Result<AppointmentRequestPage>
}
