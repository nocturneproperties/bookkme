package com.bookk.notifications.domain.impl

import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.entity.Language
import com.bookk.notifications.domain.datasource.DeviceDataSource
import kotlin.uuid.Uuid

internal class UpdateDeviceLanguage(
    private val deviceDataSource: DeviceDataSource,
    private val transactionManager: TransactionManager,
) {
    suspend operator fun invoke(deviceUuid: Uuid, language: Language): Result<Unit> = transactionManager.transaction {
        deviceDataSource.updateLanguage(deviceUuid, language)
        Unit
    }
}
