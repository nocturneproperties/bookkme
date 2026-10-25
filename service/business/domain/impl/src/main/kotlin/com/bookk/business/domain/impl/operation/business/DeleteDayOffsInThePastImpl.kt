package com.bookk.business.domain.impl.operation.business

import com.bookk.business.domain.api.business.operation.DeleteDayOffsInThePast
import com.bookk.business.domain.datasource.BusinessDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager

internal class DeleteDayOffsInThePastImpl(
    private val businessDataSource: BusinessDataSource,
    private val transactionManager: TransactionManager
) : DeleteDayOffsInThePast {
    override suspend fun invoke(): Result<Unit> = transactionManager.transaction {
        businessDataSource.deleteDayOffsInThePast()
    }
}
