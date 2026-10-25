package com.bookk.notifications.domain.impl

import com.bookk.core.domain.entity.Language
import com.bookk.core.domain.entity.runSuspendCatching
import com.bookk.notifications.domain.datasource.DeviceDataSource
import kotlin.uuid.Uuid

internal class UpdateDeviceLanguage(
    private val deviceDataSource: DeviceDataSource
) {
    suspend operator fun invoke(deviceUuid: Uuid, language: Language): Result<Unit> = runSuspendCatching {
        deviceDataSource.updateLanguage(deviceUuid, language)
        Unit
    }
}
