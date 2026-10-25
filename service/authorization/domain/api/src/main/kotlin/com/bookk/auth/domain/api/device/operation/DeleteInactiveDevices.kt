package com.bookk.auth.domain.api.device.operation

interface DeleteInactiveDevices {
    suspend operator fun invoke(): Result<Unit>
}
