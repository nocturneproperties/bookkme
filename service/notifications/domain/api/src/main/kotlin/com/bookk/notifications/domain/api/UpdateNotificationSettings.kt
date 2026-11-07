package com.bookk.notifications.domain.api

import com.bookk.notifications.domain.api.entity.NotificationSettings
import com.bookk.notifications.domain.api.entity.NotificationSettingsUpdate
import kotlin.uuid.Uuid

interface UpdateNotificationSettings {
    suspend operator fun invoke(
        userId: Uuid,
        settings: NotificationSettingsUpdate,
    ): Result<NotificationSettings>
}
