package com.bookk.notifications.domain.impl

import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.notifications.client.api.export.ExportUserNotificationData
import com.bookk.notifications.client.api.export.NotificationsUserDataExport
import com.bookk.notifications.client.api.export.NotificationsUserDataExport.ChannelExport
import com.bookk.notifications.client.api.export.NotificationsUserDataExport.DeviceExport
import com.bookk.notifications.client.api.export.NotificationsUserDataExport.SettingsExport
import com.bookk.notifications.domain.datasource.DeviceDataSource
import com.bookk.notifications.domain.datasource.NotificationSettingsDataSource
import com.bookk.notifications.domain.datasource.NotificationTargetDataSource
import kotlin.uuid.Uuid

internal class ExportUserNotificationDataImpl(
    private val settingsDataSource: NotificationSettingsDataSource,
    private val deviceDataSource: DeviceDataSource,
    private val targetDataSource: NotificationTargetDataSource,
    private val transactionManager: TransactionManager,
) : ExportUserNotificationData {

    override suspend fun invoke(userId: Uuid): Result<NotificationsUserDataExport> = transactionManager.transaction {
        NotificationsUserDataExport(
            settings = settingsDataSource.getByUserId(userId)?.let { settings ->
                SettingsExport(
                    appointmentNotificationsEnabled = settings.appointmentEnabled,
                    channels = settings.channels.map { ChannelExport(it.channel.name, it.enabled) },
                )
            },
            devices = deviceDataSource.getByUserId(userId).map {
                DeviceExport(
                    it.language.name,
                    pushNotificationsRegistered =
                    it.notificationToken != null,
                )
            },
            email = targetDataSource.getEmail(userId),
            telegram = targetDataSource.getTelegram(userId),
        )
    }
}
