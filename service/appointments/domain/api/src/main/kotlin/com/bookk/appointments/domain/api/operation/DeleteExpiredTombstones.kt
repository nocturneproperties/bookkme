package com.bookk.appointments.domain.api.operation

interface DeleteExpiredTombstones {
    suspend operator fun invoke(): Result<Unit>
}
