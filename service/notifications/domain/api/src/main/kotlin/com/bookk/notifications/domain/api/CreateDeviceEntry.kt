package com.bookk.notifications.domain.api

import com.bookk.core.domain.entity.Language
import com.bookk.notifications.domain.api.entity.Device
import kotlin.uuid.Uuid

interface CreateDeviceEntry {
    suspend operator fun invoke(
        deviceUUID: Uuid,
        authId: Uuid,
        userId: Uuid,
        language: Language,
    ): Result<Device>
}
