package com.bookk.notifications.domain.impl.notification

import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.transactionOrThrow
import com.bookk.core.domain.entity.runSuspendCatching
import com.bookk.notifications.domain.api.entity.CommunicationChannel
import com.bookk.notifications.domain.api.entity.NotificationSettings
import com.bookk.notifications.domain.datasource.NotificationSettingsDataSource
import com.bookk.notifications.domain.datasource.NotificationTargetDataSource
import com.bookk.notifications.domain.impl.channel.NotificationSender
import library.email.PlaceholderEmail
import kotlin.uuid.Uuid

internal class SendNotification(
    private val settingsDataSource: NotificationSettingsDataSource,
    private val targetDataSource: NotificationTargetDataSource,
    private val senders: Map<CommunicationChannel, NotificationSender>,
    private val transactionManager: TransactionManager,
) {
    suspend operator fun invoke(to: Uuid, notification: NotificationParameters): Result<Unit> = runSuspendCatching {
        val channels = transactionManager.transactionOrThrow { channelsFor(to, notification.type) }
        channels.forEach { channel -> senders[channel]?.send(to, notification) }
    }

    private suspend fun channelsFor(userId: Uuid, type: NotificationType): Set<CommunicationChannel> {
        val settings = requireNotNull(settingsDataSource.getByUserId(userId)) { "Settings not found" }
        if (!settings.allows(type)) return emptySet()
        return settings.enabledChannels() + mandatoryChannels(userId, type)
    }

    private suspend fun mandatoryChannels(userId: Uuid, type: NotificationType): Set<CommunicationChannel> {
        return when (type) {
            NotificationType.ACCOUNT -> accountChannels(userId)
            NotificationType.APPOINTMENT, NotificationType.EMPLOYEE -> emptySet()
        }
    }

    private suspend fun accountChannels(userId: Uuid): Set<CommunicationChannel> {
        if (hasDeliverableEmail(userId)) return setOf(CommunicationChannel.EMAIL)
        return setOf(CommunicationChannel.EMAIL, CommunicationChannel.TELEGRAM)
    }

    private suspend fun hasDeliverableEmail(userId: Uuid): Boolean {
        val email = targetDataSource.getEmail(userId) ?: return false
        return !PlaceholderEmail.isPlaceholder(email)
    }

    private fun NotificationSettings.enabledChannels(): Set<CommunicationChannel> {
        return channels.filter { it.enabled }.mapTo(mutableSetOf()) { it.channel }
    }

    private fun NotificationSettings.allows(type: NotificationType): Boolean {
        return when (type) {
            NotificationType.APPOINTMENT -> appointmentEnabled

            // Employee invitations are account-level and actionable, so they are not user-suppressible.
            // They still only go out through the channels the user has enabled.
            NotificationType.EMPLOYEE -> true

            NotificationType.ACCOUNT -> true
        }
    }
}
