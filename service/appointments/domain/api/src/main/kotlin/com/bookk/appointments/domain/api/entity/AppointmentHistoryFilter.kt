package com.bookk.appointments.domain.api.entity

import kotlin.uuid.Uuid

data class AppointmentHistoryFilter(
    val clientId: Uuid? = null,
    val employeeId: Uuid? = null,
    val statuses: Set<AppointmentStatus> = emptySet(),
    val dates: HistoryDates = HistoryDates(),
    val query: String? = null,
    val order: HistoryOrder = HistoryOrder.NEWEST_FIRST,
)
