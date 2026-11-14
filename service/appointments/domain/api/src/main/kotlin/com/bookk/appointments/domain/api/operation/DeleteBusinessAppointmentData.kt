package com.bookk.appointments.domain.api.operation

import kotlin.uuid.Uuid

interface DeleteBusinessAppointmentData {
    suspend operator fun invoke(businessId: Uuid): Result<Unit>
}
