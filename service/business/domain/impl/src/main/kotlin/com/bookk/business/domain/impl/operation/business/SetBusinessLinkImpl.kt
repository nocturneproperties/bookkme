package com.bookk.business.domain.impl.operation.business

import com.bookk.business.domain.api.business.entity.BusinessLink
import com.bookk.business.domain.api.business.operation.SetBusinessLink
import com.bookk.business.domain.datasource.BusinessDataSource
import com.bookk.business.domain.datasource.BusinessLinkDataSource
import com.bookk.business.domain.datasource.RowLock
import com.bookk.business.domain.datasource.StoredBusinessLink
import com.bookk.business.domain.impl.operation.export.requireOwner
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.entity.Error
import com.bookk.core.domain.entity.onConstraintFailure
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class SetBusinessLinkImpl(
    private val businessDataSource: BusinessDataSource,
    private val businessLinkDataSource: BusinessLinkDataSource,
    private val transactionManager: TransactionManager,
    private val clock: Clock,
) : SetBusinessLink {
    override suspend fun invoke(userId: Uuid, businessId: Uuid, name: String): Result<BusinessLink> {
        val linkName = BusinessLinkPolicy.normalize(name)
        if (!BusinessLinkPolicy.isValid(linkName)) return Result.failure(SetBusinessLink.Error.LinkInvalid())
        if (BusinessLinkPolicy.isReserved(linkName)) return Result.failure(SetBusinessLink.Error.LinkReserved())
        return transactionManager.transaction {
            businessDataSource.lockBusiness(businessId, RowLock.EXCLUSIVE) ?: throw Error.NotFound()
            val stored = businessLinkDataSource.getLinkByNameForUpdate(linkName)
            businessDataSource.requireOwner(userId, businessId)
            val now = clock.now()
            when {
                stored == null -> claim(businessId, linkName, now)

                stored.isReleasedBefore(now - BusinessLinkPolicy.HOLD_PERIOD) -> {
                    businessLinkDataSource.deleteLink(linkName)
                    claim(businessId, linkName, now)
                }

                stored.businessId != businessId -> throw SetBusinessLink.Error.LinkTaken()

                !stored.isCurrent -> restore(stored, now)
            }
            BusinessLink(businessId = businessId, name = linkName)
        }.onConstraintFailure {
            throw SetBusinessLink.Error.LinkTaken()
        }
    }

    private suspend fun claim(businessId: Uuid, name: String, now: Instant) {
        releaseCurrent(businessId, now)
        businessLinkDataSource.createLink(businessId, name)
    }

    private suspend fun restore(held: StoredBusinessLink, now: Instant) {
        releaseCurrent(held.businessId, now)
        businessLinkDataSource.restoreLink(held.businessId, held.name)
    }

    private suspend fun releaseCurrent(businessId: Uuid, now: Instant) {
        if (businessLinkDataSource.getCurrentLink(businessId) == null) return
        val changes = businessLinkDataSource.countLinkChangesSince(businessId, now - BusinessLinkPolicy.HOLD_PERIOD)
        if (changes >= BusinessLinkPolicy.MAX_CHANGES) throw SetBusinessLink.Error.ChangeLimitReached()
        businessLinkDataSource.releaseCurrentLink(businessId, now)
        businessLinkDataSource.recordLinkChange(businessId, now)
    }
}
