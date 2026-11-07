package com.bookk.user.domain.datasource

import com.bookk.user.domain.api.entity.LegalDocument
import com.bookk.user.domain.api.entity.LegalDocumentAcceptance
import com.bookk.user.domain.api.entity.LegalDocumentVersion
import kotlin.time.Instant
import kotlin.uuid.Uuid

interface LegalDocumentDataSource {
    suspend fun getVersionsInEffect(now: Instant): List<LegalDocumentVersion>
    suspend fun getVersion(document: LegalDocument, version: String): LegalDocumentVersion?
    suspend fun getAcceptances(userId: Uuid): List<LegalDocumentAcceptance>
    suspend fun recordAcceptance(userId: Uuid, acceptance: LegalDocumentAcceptance)
}
