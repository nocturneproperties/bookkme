package com.bookk.user.domain.api.operation

import com.bookk.user.domain.api.entity.LegalDocumentVersion

interface GetLegalDocuments {

    suspend operator fun invoke(): Result<List<LegalDocumentVersion>>
}
