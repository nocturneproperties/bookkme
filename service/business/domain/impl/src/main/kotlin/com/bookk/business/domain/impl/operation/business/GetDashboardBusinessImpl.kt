package com.bookk.business.domain.impl.operation.business

import com.bookk.business.domain.api.business.entity.Business
import com.bookk.business.domain.api.business.operation.GetDashboardBusiness
import com.bookk.business.domain.api.business.operation.GetDashboardBusiness.Error.NotFound
import com.bookk.business.domain.datasource.BusinessDataSource
import com.bookk.business.domain.datasource.BusinessPermissionDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import kotlin.uuid.Uuid

internal class GetDashboardBusinessImpl(
    private val businessDataSource: BusinessDataSource,
    private val businessPermissionDataSource: BusinessPermissionDataSource,
    private val transactionManager: TransactionManager
): GetDashboardBusiness {
    override suspend fun invoke(userId: Uuid): Result<Business> = transactionManager.transaction {
        val business = businessDataSource.getDashboardBusiness(userId) ?: throw NotFound()
        business.viewedBy(businessPermissionDataSource.getViewer(userId, business.id))
    }
}
