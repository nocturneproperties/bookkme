package com.bookk.appointments.domain.api.operation

import kotlin.uuid.Uuid

interface WithdrawAppointmentRequest {
    suspend operator fun invoke(userId: Uuid, requestId: Uuid): Result<Unit>
}
