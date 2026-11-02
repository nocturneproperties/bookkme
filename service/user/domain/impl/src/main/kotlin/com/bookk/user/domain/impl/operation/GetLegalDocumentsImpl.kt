package com.bookk.user.domain.impl.operation

import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.user.domain.api.entity.LegalDocumentVersion
import com.bookk.user.domain.api.operation.GetLegalDocuments
import com.bookk.user.domain.datasource.LegalDocumentDataSource
import kotlin.time.Clock

internal class GetLegalDocumentsImpl(
    private val legalDocumentDataSource: LegalDocumentDataSource,
    private val transactionManager: TransactionManager,
) : GetLegalDocuments {

    override suspend fun invoke(): Result<List<LegalDocumentVersion>> = transactionManager.transaction {
        legalDocumentDataSource.getVersionsInEffect(Clock.System.now())
    }
}
