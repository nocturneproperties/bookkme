package com.bookk.user.domain.impl.operation

import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.entity.Error
import com.bookk.user.domain.api.entity.AcceptedLegalDocuments
import com.bookk.user.domain.api.operation.AcceptLegalDocuments
import com.bookk.user.domain.datasource.LegalDocumentDataSource
import com.bookk.user.domain.datasource.UserDataSource
import kotlin.time.Clock
import kotlin.uuid.Uuid

internal class AcceptLegalDocumentsImpl(
    private val userDataSource: UserDataSource,
    private val legalDocumentDataSource: LegalDocumentDataSource,
    private val transactionManager: TransactionManager,
) : AcceptLegalDocuments {

    override suspend fun invoke(
        userId: Uuid,
        documents: AcceptedLegalDocuments,
    ): Result<Unit> = transactionManager.transaction {
        userDataSource.getUserById(userId) ?: throw Error.NotFound()
        legalDocumentDataSource.publishedAcceptances(documents, Clock.System.now())
            .forEach { legalDocumentDataSource.recordAcceptance(userId, it) }
    }
}
