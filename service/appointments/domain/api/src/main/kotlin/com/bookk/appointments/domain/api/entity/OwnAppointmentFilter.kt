package com.bookk.appointments.domain.api.entity

data class OwnAppointmentFilter(
    val period: BookingPeriod? = null,
    val statuses: Set<AppointmentStatus> = emptySet(),
)
