package com.bookk.user.domain.api.operation

interface EncryptPlaintextProfiles {

    suspend operator fun invoke(): Result<Unit>
}
