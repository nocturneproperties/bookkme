package com.bookk.notifications.domain.impl.notification

import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.transactionOrThrow
import com.bookk.core.domain.entity.runSuspendCatching
import com.bookk.notifications.domain.api.entity.CommunicationChannel
import com.bookk.notifications.domain.api.entity.NotificationSettings
import com.bookk.notifications.domain.datasource.NotificationSettingsDataSource
import com.bookk.notifications.domain.impl.channel.NotificationSender
import kotlin.uuid.Uuid

internal class SendNotification(
    val notificationDataSource: NotificationSettingsDataSource,
    val senderMap: Map<CommunicationChannel, NotificationSender>,
    private val transactionManager: TransactionManager,
) {
    suspend operator fun invoke(to: Uuid, notification: NotificationParameters): Result<Unit> = runSuspendCatching {
        val settings = transactionManager.transactionOrThrow { notificationDataSource.getByUserId(to) }
            ?: throw IllegalArgumentException("Settings not found")
        if (!settings.isTypeAllowed(notification.type)) return@runSuspendCatching
        val enabledChannels = settings.channels.filter { it.enabled }.map { it.channel }
        (enabledChannels + notification.type.mandatoryChannels).distinct().forEach { channel ->
            senderMap[channel]?.send(to, notification)
        }
    }

    private val NotificationType.mandatoryChannels: List<CommunicationChannel>
        get() = when (this) {
            NotificationType.ACCOUNT -> listOf(CommunicationChannel.EMAIL)
            NotificationType.APPOINTMENT, NotificationType.EMPLOYEE -> emptyList()
        }

    private fun NotificationSettings.isTypeAllowed(type: NotificationType): Boolean = when (type) {
        NotificationType.APPOINTMENT -> appointmentEnabled

        // Employee invitations are account-level and actionable, so they are not user-suppressible.
        // They still only go out through the channels the user has enabled.
        NotificationType.EMPLOYEE -> true

        NotificationType.ACCOUNT -> true
    }
}
