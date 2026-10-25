package com.bookk.notifications.domain.datasource

import com.bookk.notifications.domain.api.entity.NotificationChannelSettings
import com.bookk.notifications.domain.api.entity.NotificationSettings
import kotlin.uuid.Uuid

interface NotificationSettingsDataSource {
    suspend fun getByUserId(userId: Uuid): NotificationSettings?
    suspend fun upsert(settings: NotificationSettings): NotificationSettings
    suspend fun upsert(channel: NotificationChannelSettings)
    suspend fun deleteByUserId(userId: Uuid)
}
