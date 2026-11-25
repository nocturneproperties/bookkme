package com.bookk.appointments.domain.api.operation

import com.bookk.appointments.domain.api.entity.AppointmentRequestHistoryFilter
import com.bookk.appointments.domain.api.entity.AppointmentRequestPage
import kotlin.uuid.Uuid

interface GetAppointmentRequestHistory {
    suspend operator fun invoke(
        userId: Uuid,
        businessId: Uuid,
        filter: AppointmentRequestHistoryFilter,
        limit: Int?,
        cursor: String?,
    ): Result<AppointmentRequestPage>
}
