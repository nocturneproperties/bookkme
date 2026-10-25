package com.bookk.business.domain.impl.operation.business

import com.bookk.business.domain.api.business.entity.UserBusinesses
import com.bookk.business.domain.api.business.operation.GetUserBusinesses
import com.bookk.business.domain.datasource.BusinessDataSource
import com.bookk.business.domain.datasource.BusinessPermissionDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import kotlin.uuid.Uuid

internal class GetUserBusinessesImpl(
    private val businessDataSource: BusinessDataSource,
    private val businessPermissionDataSource: BusinessPermissionDataSource,
    private val transactionManager: TransactionManager
) : GetUserBusinesses {
    override suspend fun invoke(userId: Uuid): Result<UserBusinesses> = transactionManager.transaction {
        val result = businessDataSource.getUserBusinesses(userId = userId)
        val viewers = businessPermissionDataSource.getViewers(userId, result.businesses.map { it.id })
        result.copy(businesses = result.businesses.map { it.viewedBy(viewers.getValue(it.id)) })
    }
}
