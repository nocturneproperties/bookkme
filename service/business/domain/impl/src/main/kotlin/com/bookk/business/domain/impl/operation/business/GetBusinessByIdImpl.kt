package com.bookk.business.domain.impl.operation.business

import com.bookk.business.domain.api.business.entity.Business
import com.bookk.business.domain.api.business.operation.GetBusinessById
import com.bookk.business.domain.datasource.BusinessDataSource
import com.bookk.business.domain.datasource.BusinessPermissionDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import kotlin.uuid.Uuid

internal class GetBusinessByIdImpl(
    private val businessDataSource: BusinessDataSource,
    private val businessPermissionDataSource: BusinessPermissionDataSource,
    private val transactionManager: TransactionManager
) : GetBusinessById {
    override suspend fun invoke(id: Uuid, requestingUserId: Uuid): Result<Business> = transactionManager.transaction {
        val business = businessDataSource.getBusinessById(id) ?: throw GetBusinessById.Error.NotFound()
        business.viewedBy(businessPermissionDataSource.getViewer(requestingUserId, id))
    }
}
