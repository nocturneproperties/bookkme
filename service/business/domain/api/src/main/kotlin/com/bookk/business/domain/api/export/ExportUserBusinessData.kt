package com.bookk.business.domain.api.export

import kotlin.uuid.Uuid

interface ExportUserBusinessData {
    suspend operator fun invoke(userId: Uuid): Result<BusinessUserDataExport>
}
