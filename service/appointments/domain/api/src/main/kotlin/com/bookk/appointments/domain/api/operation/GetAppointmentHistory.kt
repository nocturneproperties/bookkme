package com.bookk.appointments.domain.api.operation

import com.bookk.appointments.domain.api.entity.AppointmentHistoryFilter
import com.bookk.appointments.domain.api.entity.AppointmentPage
import kotlin.uuid.Uuid

interface GetAppointmentHistory {
    suspend operator fun invoke(
        userId: Uuid,
        businessId: Uuid,
        filter: AppointmentHistoryFilter,
        limit: Int?,
        cursor: String?,
    ): Result<AppointmentPage>
}
