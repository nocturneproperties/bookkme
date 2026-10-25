package com.bookk.notifications.domain.impl

import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.notifications.domain.api.GetNotificationSettings
import com.bookk.notifications.domain.datasource.NotificationTargetDataSource
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class UpdateTargetInformation(
    private val getNotificationSettings: GetNotificationSettings,
    private val targetDataSource: NotificationTargetDataSource,
    private val transactionManager: TransactionManager
) {
    suspend operator fun invoke(userId: Uuid, target: Target, updatedAt: Instant): Result<Unit> =
        transactionManager.transaction {
            getNotificationSettings.invoke(userId).getOrThrow()

            when (target) {
                is Target.Email -> saveEmail(userId, target.newEmail, updatedAt)
                is Target.Telegram -> saveTelegram(userId, target.newTag)
            }
        }

    private suspend fun saveEmail(userId: Uuid, email: String, updatedAt: Instant) {
        if (targetDataSource.updateEmail(userId, email, updatedAt)) return
        if (targetDataSource.getEmail(userId) == null) {
            targetDataSource.insertEmail(userId, email, updatedAt)
        }
    }

    private suspend fun saveTelegram(userId: Uuid, telegramTag: String) {
        if (targetDataSource.updateTelegram(userId, telegramTag)) return
        if (targetDataSource.getTelegram(userId) == null) {
            targetDataSource.insertTelegram(userId, telegramTag)
        }
    }

    internal sealed interface Target {
        class Email(val newEmail: String) : Target
        class Telegram(val newTag: String) : Target
    }
}
