package com.bookk.business.domain.api.export

import library.export.DataExportDocument
import kotlin.uuid.Uuid

interface DownloadBusinessDataExport {
    suspend operator fun invoke(userId: Uuid, businessId: Uuid): Result<DataExportDocument>
}
