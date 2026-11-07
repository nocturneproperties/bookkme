package com.bookk.business.domain.datasource

import com.bookk.business.domain.api.export.BusinessUserDataExport
import kotlin.uuid.Uuid

interface UserDataExportDataSource {
    suspend fun export(userId: Uuid): BusinessUserDataExport
}
