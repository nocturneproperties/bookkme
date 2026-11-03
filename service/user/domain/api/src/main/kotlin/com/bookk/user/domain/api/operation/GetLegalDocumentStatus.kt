package com.bookk.user.domain.api.operation

import com.bookk.user.domain.api.entity.LegalDocumentStatus
import kotlin.uuid.Uuid

interface GetLegalDocumentStatus {

    suspend operator fun invoke(userId: Uuid): Result<List<LegalDocumentStatus>>
}
