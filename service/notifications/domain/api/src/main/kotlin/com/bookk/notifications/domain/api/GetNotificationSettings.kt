package com.bookk.notifications.domain.api

import com.bookk.notifications.domain.api.entity.NotificationSettings
import kotlin.uuid.Uuid

interface GetNotificationSettings {
    suspend operator fun invoke(userId: Uuid): Result<NotificationSettings>
}
