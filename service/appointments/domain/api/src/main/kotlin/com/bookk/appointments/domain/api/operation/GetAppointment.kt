package com.bookk.appointments.domain.api.operation

import com.bookk.appointments.domain.api.entity.Appointment
import kotlin.uuid.Uuid

interface GetAppointment {
    suspend operator fun invoke(userId: Uuid, appointmentId: Uuid): Result<Appointment>
}
