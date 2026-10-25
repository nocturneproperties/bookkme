package com.bookk.appointments.domain.api.operation

import com.bookk.appointments.domain.api.entity.Appointment
import com.bookk.appointments.domain.api.entity.AppointmentCancellation
import kotlin.uuid.Uuid

interface CancelAppointment {
    suspend operator fun invoke(userId: Uuid, cancellation: AppointmentCancellation): Result<Appointment>
}
