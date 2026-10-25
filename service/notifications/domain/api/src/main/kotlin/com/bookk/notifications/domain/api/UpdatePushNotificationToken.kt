package com.bookk.notifications.domain.api

import com.bookk.notifications.domain.api.entity.Device
import kotlin.uuid.Uuid

interface UpdatePushNotificationToken {
    suspend operator fun invoke(deviceUuid: Uuid, token: String): Result<Device>
}