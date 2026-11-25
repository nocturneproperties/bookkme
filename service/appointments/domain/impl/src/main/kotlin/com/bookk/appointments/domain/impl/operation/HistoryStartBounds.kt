package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.api.entity.HistoryDates
import com.bookk.appointments.domain.api.entity.StartBounds
import com.bookk.appointments.domain.datasource.AppointmentSettingsDataSource
import com.bookk.core.domain.entity.Error
import kotlin.uuid.Uuid

internal suspend fun AppointmentSettingsDataSource.startBounds(businessId: Uuid, dates: HistoryDates): StartBounds {
    if (dates.isOpen) return StartBounds.OPEN
    val settings = get(businessId) ?: throw Error.NotFound()
    return dates.startBounds(settings.timeZone)
}
