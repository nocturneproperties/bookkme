package com.bookk.notifications.domain.api

import kotlin.uuid.Uuid

interface DeleteDeviceByUUID {
    suspend operator fun invoke(deviceUUID: Uuid): Result<Unit>
}
