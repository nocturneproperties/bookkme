package com.bookk.notifications.domain.impl

import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.notifications.domain.api.UpdateNotificationSettings
import com.bookk.notifications.domain.api.entity.NotificationSettings
import com.bookk.notifications.domain.datasource.NotificationSettingsDataSource
import kotlin.uuid.Uuid

internal class UpdateNotificationSettingsImpl(
    private val notificationSettingsDataSource: NotificationSettingsDataSource,
    private val transactionManager: TransactionManager,
) : UpdateNotificationSettings {
    override suspend fun invoke(userId: Uuid, settings: NotificationSettings.Update): Result<NotificationSettings> =
        transactionManager.transaction {
            val settings = notificationSettingsDataSource.upsert(
                NotificationSettings(
                    id = settings.id,
                    userId = userId,
                    appointmentEnabled = settings.appointmentEnabled,
                    channels = settings.channels
                )
            )
            settings.copy(channels = settings.channels.filter { it.availableToClients })
        }
}
