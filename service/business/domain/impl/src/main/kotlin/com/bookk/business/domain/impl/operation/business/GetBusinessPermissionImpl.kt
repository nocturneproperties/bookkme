package com.bookk.business.domain.impl.operation.business

import com.bookk.business.domain.api.business.entity.BusinessResource
import com.bookk.business.domain.api.business.operation.GetBusinessPermission
import com.bookk.business.domain.datasource.BusinessPermissionDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import library.permissions.ResourcePermission
import kotlin.uuid.Uuid

internal class GetBusinessPermissionImpl(
    private val businessPermissionDataSource: BusinessPermissionDataSource,
    private val transactionManager: TransactionManager
) : GetBusinessPermission {
    override suspend fun invoke(userId: Uuid, businessId: Uuid, resource: BusinessResource): Result<ResourcePermission> =
        transactionManager.transaction {
            businessPermissionDataSource.getPermission(userId, businessId, resource)
        }
}
