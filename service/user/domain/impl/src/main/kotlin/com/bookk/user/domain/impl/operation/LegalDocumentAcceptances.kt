package com.bookk.user.domain.impl.operation

import com.bookk.user.domain.api.entity.AcceptedLegalDocuments
import com.bookk.user.domain.api.entity.LegalDocumentAcceptance
import com.bookk.user.domain.api.entity.LegalDocumentError
import com.bookk.user.domain.datasource.LegalDocumentDataSource
import kotlin.time.Instant

internal suspend fun LegalDocumentDataSource.publishedAcceptances(
    documents: AcceptedLegalDocuments,
    now: Instant,
): List<LegalDocumentAcceptance> = documents.versions().map { (document, version) ->
    val published = getVersion(document, version)
    if (published == null || !published.isInEffect(now)) throw LegalDocumentError.VersionUnknown()
    LegalDocumentAcceptance(document, version, acceptedAt = now)
}
