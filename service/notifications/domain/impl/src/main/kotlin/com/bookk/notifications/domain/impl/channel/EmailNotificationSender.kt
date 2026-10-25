package com.bookk.notifications.domain.impl.channel

import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.notifications.domain.datasource.NotificationTargetDataSource
import com.bookk.notifications.domain.impl.notification.NotificationParameters
import kotlin.uuid.Uuid

internal class EmailNotificationSender(
    private val transactionManager: TransactionManager,
    private val targetDataSource: NotificationTargetDataSource
) : NotificationSender {
    override suspend fun send(toUserId: Uuid, params: NotificationParameters): Result<Unit> {
        return Result.failure(UnsupportedOperationException("Not implemented yet"))
    }
}