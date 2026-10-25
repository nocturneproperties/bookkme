package com.bookk.appointments.domain.api.operation

import com.bookk.appointments.domain.api.entity.AppointmentRequest
import kotlin.uuid.Uuid

interface GetPendingAppointmentRequests {
    suspend operator fun invoke(userId: Uuid, businessId: Uuid): Result<List<AppointmentRequest>>
}
