package com.bookk.notifications.domain.impl.notification

import com.bookk.core.domain.entity.runSuspendCatching
import com.bookk.notifications.domain.api.entity.CommunicationChannel
import com.bookk.notifications.domain.api.entity.NotificationSettings
import com.bookk.notifications.domain.datasource.NotificationSettingsDataSource
import com.bookk.notifications.domain.impl.channel.NotificationSender
import kotlin.uuid.Uuid

internal class SendNotification(
    val notificationDataSource: NotificationSettingsDataSource,
    val senderMap: Map<CommunicationChannel, NotificationSender>
) {
    suspend operator fun invoke(to: Uuid, notification: NotificationParameters): Result<Unit> = runSuspendCatching {
        val settings = notificationDataSource.getByUserId(to) ?: throw IllegalArgumentException("Settings not found")
        if (!settings.isTypeAllowed(notification.type)) return@runSuspendCatching
        val enabledChannelSettings = settings.channels.filter { it.enabled }
        enabledChannelSettings.forEach { settings ->
            senderMap[settings.channel]?.send(to, notification)
        }
    }


    private fun NotificationSettings.isTypeAllowed(type: NotificationType): Boolean {
        return when (type) {
            NotificationType.APPOINTMENT -> appointmentEnabled
            // Employee invitations are account-level and actionable, so they are not user-suppressible.
            // They still only go out through the channels the user has enabled.
            NotificationType.EMPLOYEE -> true
        }
    }
}