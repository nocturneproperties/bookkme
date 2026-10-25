package com.bookk.appointments.domain.api.operation

interface MarkAppointmentsCompleted {
    suspend operator fun invoke(): Result<Unit>
}
