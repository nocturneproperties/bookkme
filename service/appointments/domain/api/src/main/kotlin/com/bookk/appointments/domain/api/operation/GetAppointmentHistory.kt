package com.bookk.appointments.domain.api.operation

import com.bookk.appointments.domain.api.entity.AppointmentPagination
import kotlin.uuid.Uuid

interface GetAppointmentHistory {
    suspend operator fun invoke(
        userId: Uuid,
        businessId: Uuid,
        limit: Int,
        offset: Long,
        query: String? = null
    ): Result<AppointmentPagination>
}