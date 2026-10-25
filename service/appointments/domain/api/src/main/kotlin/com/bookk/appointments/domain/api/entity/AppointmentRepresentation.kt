package com.bookk.appointments.domain.api.entity

import kotlin.time.Instant
import kotlin.uuid.Uuid

interface AppointmentRepresentation {
    val id: Uuid
    val userId: Uuid
    val businessId: Uuid
    val employee: EmployeeSnapshot
    val date: Instant
    val dateEnd: Instant
}