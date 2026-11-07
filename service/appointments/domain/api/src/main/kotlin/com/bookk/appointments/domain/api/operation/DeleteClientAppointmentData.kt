package com.bookk.appointments.domain.api.operation

import kotlin.uuid.Uuid

interface DeleteClientAppointmentData {
    suspend operator fun invoke(businessId: Uuid, clientId: Uuid): Result<Unit>
}
