package com.bookk.appointments.domain.api.operation

interface DeleteOutdatedRequests {
    suspend operator fun invoke(): Result<Unit>
}
