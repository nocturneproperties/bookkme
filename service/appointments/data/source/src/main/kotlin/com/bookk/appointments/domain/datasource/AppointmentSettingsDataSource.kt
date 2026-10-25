package com.bookk.appointments.domain.datasource

import com.bookk.appointments.domain.api.entity.AppointmentSettings
import com.bookk.appointments.domain.api.entity.AppointmentSettingsUpdate
import kotlin.uuid.Uuid

interface AppointmentSettingsDataSource {
    suspend fun create(settings: AppointmentSettings): AppointmentSettings
    suspend fun update(update: AppointmentSettingsUpdate): AppointmentSettings
    suspend fun get(businessId: Uuid): AppointmentSettings?
    suspend fun getForUpdate(businessId: Uuid): AppointmentSettings?
}
