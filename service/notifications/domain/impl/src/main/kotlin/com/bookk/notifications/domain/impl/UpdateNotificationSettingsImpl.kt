package com.bookk.notifications.domain.impl

import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.notifications.domain.api.UpdateNotificationSettings
import com.bookk.notifications.domain.api.entity.CommunicationChannel
import com.bookk.notifications.domain.api.entity.NotificationChannelSettings
import com.bookk.notifications.domain.api.entity.NotificationSettings
import com.bookk.notifications.domain.api.entity.NotificationSettingsUpdate
import com.bookk.notifications.domain.datasource.NotificationSettingsDataSource
import kotlin.uuid.Uuid

internal class UpdateNotificationSettingsImpl(
    private val notificationSettingsDataSource: NotificationSettingsDataSource,
    private val transactionManager: TransactionManager,
) : UpdateNotificationSettings {
    override suspend fun invoke(
        userId: Uuid,
        settings: NotificationSettingsUpdate,
    ): Result<NotificationSettings> = transactionManager.transaction {
        val stored = notificationSettingsDataSource.getByUserId(userId) ?: NotificationSettings(userId = userId)
        val requested = settings.channels.associate { it.channel to it.enabled }
        val saved = notificationSettingsDataSource.upsert(
            stored.copy(
                appointmentEnabled = settings.appointmentEnabled,
                channels = stored.channels.map { it.withRequestedState(requested) },
            ),
        )
        saved.copy(channels = saved.channels.filter { it.availableToClients })
    }

    private fun NotificationChannelSettings.withRequestedState(
        requested: Map<CommunicationChannel, Boolean>,
    ): NotificationChannelSettings {
        if (!availableToClients) return this
        return copy(enabled = requested[channel] ?: enabled)
    }
}
