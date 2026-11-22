package com.bookk.business.domain.impl.operation.business

import com.bookk.business.domain.api.business.operation.DeleteReleasedBusinessLinks
import com.bookk.business.domain.datasource.BusinessLinkDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import kotlin.time.Clock

internal class DeleteReleasedBusinessLinksImpl(
    private val businessLinkDataSource: BusinessLinkDataSource,
    private val transactionManager: TransactionManager,
    private val clock: Clock,
) : DeleteReleasedBusinessLinks {
    override suspend fun invoke(): Result<Unit> = transactionManager.transaction {
        val holdStart = clock.now() - BusinessLinkPolicy.HOLD_PERIOD
        businessLinkDataSource.deleteLinksReleasedBefore(holdStart)
        businessLinkDataSource.deleteLinkChangesBefore(holdStart)
    }
}
