package com.bookk.appointments.domain.api.operation

import kotlin.uuid.Uuid

interface DeleteUserAppointmentData {
    suspend operator fun invoke(userId: Uuid): Result<Unit>
}
