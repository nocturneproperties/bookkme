package com.bookk.business.domain.impl.operation.business

import com.bookk.business.domain.api.business.entity.BusinessResource
import com.bookk.business.domain.api.business.operation.SetDashboardBusiness
import com.bookk.business.domain.datasource.BusinessDataSource
import com.bookk.business.domain.datasource.BusinessPermissionDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import library.permissions.PermissionAction
import library.permissions.assert
import kotlin.uuid.Uuid

internal class SetDashboardBusinessImpl(
    private val businessDataSource: BusinessDataSource,
    private val businessPermissionDataSource: BusinessPermissionDataSource,
    private val transactionManager: TransactionManager
) : SetDashboardBusiness {
    override suspend fun invoke(userId: Uuid, businessId: Uuid): Result<Unit> = transactionManager.transaction {
        businessPermissionDataSource.getPermission(userId, businessId, BusinessResource.BUSINESS).assert(PermissionAction.VIEW)
        businessDataSource.setDashboardBusiness(userId, businessId)
    }
}
