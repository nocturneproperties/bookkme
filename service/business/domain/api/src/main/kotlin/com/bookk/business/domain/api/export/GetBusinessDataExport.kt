package com.bookk.business.domain.api.export

import library.export.DataExport
import kotlin.uuid.Uuid

interface GetBusinessDataExport {
    suspend operator fun invoke(userId: Uuid, businessId: Uuid): Result<DataExport>
}
