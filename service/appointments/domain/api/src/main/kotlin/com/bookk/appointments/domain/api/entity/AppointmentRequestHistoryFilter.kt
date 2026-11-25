package com.bookk.appointments.domain.api.entity

import kotlin.uuid.Uuid

data class AppointmentRequestHistoryFilter(
    val clientId: Uuid? = null,
    val employeeId: Uuid? = null,
    val statuses: Set<AppointmentRequestStatus> = emptySet(),
    val dates: HistoryDates = HistoryDates(),
    val order: HistoryOrder = HistoryOrder.NEWEST_FIRST,
)
