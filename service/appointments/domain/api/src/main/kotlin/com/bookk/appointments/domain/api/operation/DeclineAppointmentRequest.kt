package com.bookk.appointments.domain.api.operation

import com.bookk.appointments.domain.api.entity.AppointmentCancellation
import kotlin.uuid.Uuid

interface DeclineAppointmentRequest {
    suspend operator fun invoke(userId: Uuid, cancellation: AppointmentCancellation): Result<Unit>
}
