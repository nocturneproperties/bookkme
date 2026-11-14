package com.bookk.business.domain.impl.operation.client

import com.bookk.business.domain.api.client.operation.RecordClientVisit
import com.bookk.business.domain.datasource.ClientDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class RecordClientVisitImpl(
    private val clientDataSource: ClientDataSource,
    private val transactionManager: TransactionManager,
) : RecordClientVisit {
    override suspend fun invoke(
        businessId: Uuid,
        clientId: Uuid,
        visitEnd: Instant,
    ): Result<Unit> = transactionManager.transaction {
        clientDataSource.recordVisit(businessId, clientId, visitEnd)
    }
}
