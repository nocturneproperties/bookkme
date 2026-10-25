package com.bookk.business.domain.impl.operation.client

import com.bookk.business.domain.api.client.operation.GetClientBusinessIds
import com.bookk.business.domain.datasource.ClientDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import kotlin.uuid.Uuid

internal class GetClientBusinessIdsImpl(
    private val clientDataSource: ClientDataSource,
    private val transactionManager: TransactionManager
) : GetClientBusinessIds {
    override suspend fun invoke(userId: Uuid): Result<List<Uuid>> = transactionManager.transaction {
        clientDataSource.getBusinessIdsByUserId(userId)
    }
}
