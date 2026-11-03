package com.bookk.user.domain.api.operation

import library.export.DataExport
import kotlin.uuid.Uuid

interface GetPersonalDataExport {
    suspend operator fun invoke(userId: Uuid): Result<DataExport>
}
