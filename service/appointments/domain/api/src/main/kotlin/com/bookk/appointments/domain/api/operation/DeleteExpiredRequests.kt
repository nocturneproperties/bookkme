package com.bookk.appointments.domain.api.operation

interface DeleteExpiredRequests {
    suspend operator fun invoke(): Result<Unit>
}
