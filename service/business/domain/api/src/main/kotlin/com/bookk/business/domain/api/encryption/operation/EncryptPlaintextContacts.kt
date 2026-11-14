package com.bookk.business.domain.api.encryption.operation

interface EncryptPlaintextContacts {

    suspend operator fun invoke(): Result<Unit>
}
