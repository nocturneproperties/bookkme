package com.bookk.notifications.domain.impl

import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.entity.Language
import com.bookk.notifications.domain.api.CreateDeviceEntry
import com.bookk.notifications.domain.datasource.DeviceDataSource
import kotlin.uuid.Uuid

internal class CreateDeviceEntryImpl(
    private val deviceDataSource: DeviceDataSource,
    private val transactionManager: TransactionManager,
) : CreateDeviceEntry {
    override suspend fun invoke(deviceUUID: Uuid, authId: Uuid, userId: Uuid, language: Language) = transactionManager.transaction {
        deviceDataSource.create(authId = authId, deviceUUID = deviceUUID, userId = userId, language = language)
    }
}
