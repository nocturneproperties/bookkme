package com.bookk.business.domain.impl.operation.business

import com.bookk.business.domain.api.business.entity.BusinessLink
import com.bookk.business.domain.api.business.operation.ResolveBusinessLink
import com.bookk.business.domain.datasource.BusinessLinkDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import kotlin.time.Clock

internal class ResolveBusinessLinkImpl(
    private val businessLinkDataSource: BusinessLinkDataSource,
    private val transactionManager: TransactionManager,
    private val clock: Clock,
) : ResolveBusinessLink {
    override suspend fun invoke(name: String): Result<BusinessLink> {
        val linkName = BusinessLinkPolicy.normalize(name)
        if (!BusinessLinkPolicy.isValid(linkName)) return Result.failure(ResolveBusinessLink.Error.LinkNotFound())
        return transactionManager.transaction {
            val holdStart = clock.now() - BusinessLinkPolicy.HOLD_PERIOD
            val stored = businessLinkDataSource.getLinkByName(linkName)
                ?.takeUnless { it.isReleasedBefore(holdStart) }
                ?: throw ResolveBusinessLink.Error.LinkNotFound()
            if (stored.isCurrent) {
                stored.link()
            } else {
                businessLinkDataSource.getCurrentLink(stored.businessId) ?: stored.link()
            }
        }
    }
}
