package com.bookk.appointments.domain.api.operation

interface IndexClientNames {
    suspend operator fun invoke(): Result<Unit>
}
