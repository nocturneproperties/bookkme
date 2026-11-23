package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.api.entity.AppointmentRepresentation
import com.bookk.appointments.domain.api.entity.AppointmentSettings
import com.bookk.appointments.domain.api.entity.BookedRange
import com.bookk.appointments.domain.api.entity.BookingConflictError
import com.bookk.appointments.domain.datasource.AppointmentDataSource
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class EmployeeBookings(
    private val appointmentDataSource: AppointmentDataSource,
) {
    suspend fun overlapping(businessId: Uuid, employeeId: Uuid, range: OpenEndRange<Instant>): List<BookedRange> {
        return appointmentDataSource.getEmployeeBookings(businessId, employeeId, range)
    }

    suspend fun requireFree(booking: AppointmentRepresentation, settings: AppointmentSettings) {
        val gap = settings.bookingGap()
        val searched = booking.date - gap..<booking.dateEnd + gap
        val others = overlapping(booking.businessId, booking.employee.id, searched).filter { it.id != booking.id }
        if (others.isNotEmpty()) throw BookingConflictError.EmployeeBusy()
    }
}

internal fun AppointmentSettings.bookingGap(): Duration {
    return inBetweenBreakInMinutes.minutes
}
