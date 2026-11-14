package com.bookk.appointments.domain.api.operation

interface FillBusinessOwners {
    suspend operator fun invoke(): Result<Unit>
}
