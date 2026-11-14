package com.bookk.notifications.domain.api

interface EncryptPlaintextTargets {

    suspend operator fun invoke(): Result<Unit>
}
