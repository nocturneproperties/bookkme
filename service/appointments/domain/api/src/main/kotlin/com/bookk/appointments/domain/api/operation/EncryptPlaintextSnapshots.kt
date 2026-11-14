package com.bookk.appointments.domain.api.operation

interface EncryptPlaintextSnapshots {
    suspend operator fun invoke(): Result<Unit>
}
