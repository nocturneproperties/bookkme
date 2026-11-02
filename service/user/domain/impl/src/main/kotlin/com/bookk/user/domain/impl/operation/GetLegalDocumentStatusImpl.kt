package com.bookk.user.domain.impl.operation

import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.user.domain.api.entity.LegalDocumentStatus
import com.bookk.user.domain.api.operation.GetLegalDocumentStatus
import com.bookk.user.domain.datasource.LegalDocumentDataSource
import kotlin.time.Clock
import kotlin.uuid.Uuid

internal class GetLegalDocumentStatusImpl(
    private val legalDocumentDataSource: LegalDocumentDataSource,
    private val transactionManager: TransactionManager,
) : GetLegalDocumentStatus {

    override suspend fun invoke(userId: Uuid): Result<List<LegalDocumentStatus>> = transactionManager.transaction {
        val versionsInEffect = legalDocumentDataSource.getVersionsInEffect(Clock.System.now())
        val acceptedAtByVersion = legalDocumentDataSource.getAcceptances(userId)
            .associate { (it.document to it.version) to it.acceptedAt }
        versionsInEffect.map { current ->
            LegalDocumentStatus(current, acceptedAt = acceptedAtByVersion[current.document to current.version])
        }
    }
}
