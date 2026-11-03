package com.bookk.user.domain.api.operation

import library.export.DataExportDocument
import kotlin.uuid.Uuid

interface DownloadPersonalDataExport {
    suspend operator fun invoke(userId: Uuid): Result<DataExportDocument>
}
