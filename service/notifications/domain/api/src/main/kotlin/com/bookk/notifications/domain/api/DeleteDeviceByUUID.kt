package com.bookk.notifications.domain.api

import kotlin.uuid.Uuid

interface DeleteDeviceByUUID {
    suspend operator fun invoke(authId: Uuid, deviceUUID: Uuid): Result<Unit>
}
