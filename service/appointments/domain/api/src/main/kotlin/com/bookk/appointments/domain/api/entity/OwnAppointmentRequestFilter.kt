package com.bookk.appointments.domain.api.entity

data class OwnAppointmentRequestFilter(
    val period: BookingPeriod? = null,
    val statuses: Set<AppointmentRequestStatus> = emptySet(),
)
