package com.bookk.appointments.domain.api.operation

import com.bookk.appointments.domain.api.entity.Appointment
import kotlinx.datetime.LocalDate
import kotlin.uuid.Uuid

interface GetAppointmentsForDate {
    suspend operator fun invoke(
        userId: Uuid,
        businessId: Uuid,
        date: LocalDate,
        employeeId: Uuid?,
    ): Result<List<Appointment>>
}