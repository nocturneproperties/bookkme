package com.bookk.business.domain.impl.operation.service

import com.bookk.business.domain.api.business.entity.BusinessResource
import com.bookk.business.domain.api.service.operation.DeleteServiceGroup
import com.bookk.business.domain.datasource.BusinessDataSource
import com.bookk.business.domain.datasource.BusinessPermissionDataSource
import com.bookk.business.domain.datasource.RowLock
import com.bookk.business.domain.datasource.ServiceDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.entity.Error
import library.permissions.PermissionAction
import library.permissions.assert
import kotlin.uuid.Uuid

internal class DeleteServiceGroupImpl(
    private val dataSource: ServiceDataSource,
    private val businessDataSource: BusinessDataSource,
    private val businessPermissionDataSource: BusinessPermissionDataSource,
    private val transactionManager: TransactionManager
) : DeleteServiceGroup {

    override suspend fun invoke(requestUserId: Uuid, businessId: Uuid, id: Uuid): Result<Unit> {
        return transactionManager.transaction {
            businessDataSource.lockBusiness(businessId, RowLock.EXCLUSIVE) ?: throw Error.NotFound()
            businessPermissionDataSource.getPermission(requestUserId, businessId, BusinessResource.SERVICES)
                .assert(PermissionAction.DELETE)
            dataSource.deleteServiceGroup(id, businessId)
        }
    }
}