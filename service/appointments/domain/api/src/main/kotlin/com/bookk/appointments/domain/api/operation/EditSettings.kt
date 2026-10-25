package com.bookk.appointments.domain.api.operation

import com.bookk.appointments.domain.api.entity.AppointmentSettings
import com.bookk.appointments.domain.api.entity.AppointmentSettingsUpdate
import kotlin.uuid.Uuid

interface EditSettings {
    suspend operator fun invoke(userId: Uuid, update: AppointmentSettingsUpdate): Result<AppointmentSettings>
}
