package com.bookk.business.domain.impl.operation.business

import com.bookk.business.domain.api.business.entity.BusinessLink
import com.bookk.business.domain.api.business.entity.BusinessResource
import com.bookk.business.domain.api.business.operation.GetBusinessLink
import com.bookk.business.domain.datasource.BusinessLinkDataSource
import com.bookk.business.domain.datasource.BusinessPermissionDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import library.permissions.PermissionAction
import library.permissions.assert
import kotlin.uuid.Uuid

internal class GetBusinessLinkImpl(
    private val businessLinkDataSource: BusinessLinkDataSource,
    private val businessPermissionDataSource: BusinessPermissionDataSource,
    private val transactionManager: TransactionManager,
) : GetBusinessLink {
    override suspend fun invoke(userId: Uuid, businessId: Uuid): Result<BusinessLink> = transactionManager.transaction {
        businessPermissionDataSource.getPermission(userId, businessId, BusinessResource.BUSINESS)
            .assert(PermissionAction.VIEW)
        businessLinkDataSource.getCurrentLink(businessId) ?: throw GetBusinessLink.Error.LinkNotFound()
    }
}
