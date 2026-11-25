package com.bookk.appointments.domain.api.operation

import com.bookk.appointments.domain.api.entity.AppointmentPage
import com.bookk.appointments.domain.api.entity.OwnAppointmentFilter
import kotlin.uuid.Uuid

interface GetOwnAppointmentHistory {
    suspend operator fun invoke(
        userId: Uuid,
        filter: OwnAppointmentFilter,
        limit: Int?,
        cursor: String?,
    ): Result<AppointmentPage>
}
