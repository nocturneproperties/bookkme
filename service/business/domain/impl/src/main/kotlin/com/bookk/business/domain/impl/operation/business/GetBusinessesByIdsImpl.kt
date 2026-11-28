package com.bookk.business.domain.impl.operation.business

import com.bookk.business.domain.api.business.entity.Business
import com.bookk.business.domain.api.business.operation.GetBusinessesByIds
import com.bookk.business.domain.datasource.BusinessDataSource
import com.bookk.business.domain.datasource.BusinessPermissionDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import kotlin.uuid.Uuid

internal class GetBusinessesByIdsImpl(
    private val businessDataSource: BusinessDataSource,
    private val businessPermissionDataSource: BusinessPermissionDataSource,
    private val transactionManager: TransactionManager,
) : GetBusinessesByIds {
    override suspend fun invoke(ids: List<Uuid>, requestingUserId: Uuid): Result<List<Business>> {
        if (ids.size > GetBusinessesByIds.MAX_IDS) return Result.failure(GetBusinessesByIds.Error.TooManyIds())
        val requestedIds = ids.distinct()
        if (requestedIds.isEmpty()) return Result.success(emptyList())
        return transactionManager.transaction {
            val businessesById = businessDataSource.getBusinessesByIds(requestedIds).associateBy { it.id }
            val found = requestedIds.mapNotNull { businessesById[it] }
            val viewers = businessPermissionDataSource.getViewers(requestingUserId, found.map { it.id })
            found.map { it.viewedBy(viewers.getValue(it.id)) }
        }
    }
}
