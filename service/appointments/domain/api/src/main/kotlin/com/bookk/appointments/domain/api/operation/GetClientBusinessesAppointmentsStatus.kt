package com.bookk.appointments.domain.api.operation

import com.bookk.appointments.domain.api.entity.BusinessAppointmentsEnabled
import kotlin.uuid.Uuid

interface GetClientBusinessesAppointmentsStatus {
    suspend operator fun invoke(userId: Uuid): Result<List<BusinessAppointmentsEnabled>>
}
