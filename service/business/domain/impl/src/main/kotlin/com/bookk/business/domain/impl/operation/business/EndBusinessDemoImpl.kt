package com.bookk.business.domain.impl.operation.business

import com.bookk.business.domain.api.business.operation.EndBusinessDemo
import com.bookk.business.domain.datasource.BusinessDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import kotlin.uuid.Uuid

internal class EndBusinessDemoImpl(
    private val businessDataSource: BusinessDataSource,
    private val transactionManager: TransactionManager,
) : EndBusinessDemo {
    override suspend fun invoke(ownerUserId: Uuid): Result<Unit> = transactionManager.transaction {
        businessDataSource.endDemoOfBusinessesOwnedBy(ownerUserId)
    }
}
