package com.bookk.notifications.domain.impl

import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.notifications.domain.api.DeleteDeviceByUUID
import com.bookk.notifications.domain.datasource.DeviceDataSource
import kotlin.uuid.Uuid

internal class DeleteDeviceByUUIDImpl(
    private val deviceDataSource: DeviceDataSource,
    private val transactionManager: TransactionManager,
) : DeleteDeviceByUUID {
    override suspend fun invoke(deviceUUID: Uuid) = transactionManager.transaction {
        deviceDataSource.deleteByDeviceUuid(deviceUUID)
    }
}
