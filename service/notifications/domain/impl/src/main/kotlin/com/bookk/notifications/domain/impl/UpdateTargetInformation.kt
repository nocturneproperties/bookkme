package com.bookk.notifications.domain.impl

import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.notifications.domain.api.GetNotificationSettings
import com.bookk.notifications.domain.api.entity.CommunicationChannel
import com.bookk.notifications.domain.datasource.NotificationSettingsDataSource
import com.bookk.notifications.domain.datasource.NotificationTargetDataSource
import library.encryption.SubjectKeys
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class UpdateTargetInformation(
    private val getNotificationSettings: GetNotificationSettings,
    private val targetDataSource: NotificationTargetDataSource,
    private val notificationSettingsDataSource: NotificationSettingsDataSource,
    private val subjectKeys: SubjectKeys,
    private val transactionManager: TransactionManager,
) {
    suspend operator fun invoke(
        userId: Uuid,
        targets: List<Target>,
        updatedAt: Instant,
    ): Result<Unit> = transactionManager.transaction {
        if (subjectKeys.isShredded(userId)) return@transaction
        getNotificationSettings.invoke(userId).getOrThrow()

        targets.forEach { target ->
            when (target) {
                is Target.Email -> saveEmail(userId, target.newEmail, updatedAt)
                is Target.Telegram -> saveTelegram(userId, target.telegramUserId.toString())
            }
        }
    }

    private suspend fun saveEmail(userId: Uuid, email: String, updatedAt: Instant) {
        if (targetDataSource.updateEmail(userId, email, updatedAt)) return
        if (!targetDataSource.hasEmail(userId)) {
            targetDataSource.insertEmail(userId, email, updatedAt)
        }
    }

    private suspend fun saveTelegram(userId: Uuid, telegramUserId: String) {
        val updated = targetDataSource.updateTelegram(userId, telegramUserId)
        val isFirstTarget = !updated && !targetDataSource.hasTelegram(userId)
        if (isFirstTarget) {
            targetDataSource.insertTelegram(userId, telegramUserId)
        }
        offerTelegram(userId, turnOn = isFirstTarget)
    }

    private suspend fun offerTelegram(userId: Uuid, turnOn: Boolean) {
        val settings = notificationSettingsDataSource.getByUserId(userId) ?: return
        val telegram = settings.channels.firstOrNull { it.channel == CommunicationChannel.TELEGRAM } ?: return
        val offered = telegram.copy(availableToClients = true, enabled = telegram.enabled || turnOn)
        if (offered == telegram) return
        notificationSettingsDataSource.upsert(offered)
    }

    internal sealed interface Target {
        class Email(val newEmail: String) : Target
        class Telegram(val telegramUserId: Long) : Target
    }
}
