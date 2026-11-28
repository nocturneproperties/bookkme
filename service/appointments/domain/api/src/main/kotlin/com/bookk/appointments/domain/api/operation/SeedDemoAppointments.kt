package com.bookk.appointments.domain.api.operation

import kotlin.uuid.Uuid

interface SeedDemoAppointments {
    suspend operator fun invoke(
        businessId: Uuid,
        ownerUserId: Uuid,
        employeeId: Uuid,
        serviceIds: List<Uuid>,
        clientIds: List<Uuid>,
    ): Result<Unit>
}
