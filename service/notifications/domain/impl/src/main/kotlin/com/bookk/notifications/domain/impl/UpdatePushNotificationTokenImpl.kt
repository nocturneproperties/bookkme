package com.bookk.notifications.domain.impl

import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.notifications.domain.api.UpdatePushNotificationToken
import com.bookk.notifications.domain.datasource.DeviceDataSource
import kotlin.uuid.Uuid

internal class UpdatePushNotificationTokenImpl(
    private val deviceDataSource: DeviceDataSource,
    private val transactionManager: TransactionManager,
) : UpdatePushNotificationToken {
    override suspend fun invoke(authId: Uuid, deviceUuid: Uuid, token: String) = transactionManager.transaction {
        deviceDataSource.updateToken(authId = authId, deviceUuid = deviceUuid, token = token)
    }
}
