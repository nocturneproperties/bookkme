package com.bookk.appointments.domain.api.operation

import com.bookk.appointments.domain.api.entity.AppointmentSettings
import kotlin.uuid.Uuid

interface GetSettings {
    suspend operator fun invoke(userId: Uuid, businessId: Uuid): Result<AppointmentSettings>
}
