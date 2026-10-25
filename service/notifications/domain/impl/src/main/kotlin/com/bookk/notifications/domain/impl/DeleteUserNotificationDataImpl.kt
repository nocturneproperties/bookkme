package com.bookk.notifications.domain.impl

import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.notifications.domain.api.DeleteUserNotificationData
import com.bookk.notifications.domain.datasource.NotificationSettingsDataSource
import com.bookk.notifications.domain.datasource.NotificationTargetDataSource
import kotlin.uuid.Uuid

internal class DeleteUserNotificationDataImpl(
    private val notificationSettingsDataSource: NotificationSettingsDataSource,
    private val notificationTargetDataSource: NotificationTargetDataSource,
    private val transactionManager: TransactionManager,
) : DeleteUserNotificationData {
    override suspend fun invoke(userId: Uuid): Result<Unit> = transactionManager.transaction {
        notificationSettingsDataSource.deleteByUserId(userId)
        notificationTargetDataSource.deleteByUserId(userId)
    }
}
