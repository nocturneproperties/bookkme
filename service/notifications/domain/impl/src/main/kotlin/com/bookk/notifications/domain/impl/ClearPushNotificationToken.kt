package com.bookk.notifications.domain.impl

import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.notifications.domain.datasource.DeviceDataSource
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class ClearPushNotificationToken(
    private val deviceDataSource: DeviceDataSource,
    private val transactionManager: TransactionManager,
) {
    suspend operator fun invoke(
        authId: Uuid,
        deviceUuid: Uuid,
        signedOutAt: Instant,
    ): Result<Unit> = transactionManager.transaction {
        deviceDataSource.clearTokenRegisteredBefore(authId, deviceUuid, signedOutAt)
    }
}
