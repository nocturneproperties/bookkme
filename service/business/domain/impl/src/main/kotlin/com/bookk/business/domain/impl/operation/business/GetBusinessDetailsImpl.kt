package com.bookk.business.domain.impl.operation.business

import com.bookk.business.domain.api.business.entity.Business
import com.bookk.business.domain.api.business.operation.GetBusinessDetails
import com.bookk.business.domain.datasource.BusinessDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import kotlin.uuid.Uuid

internal class GetBusinessDetailsImpl(
    private val businessDataSource: BusinessDataSource,
    private val transactionManager: TransactionManager
) : GetBusinessDetails {
    override suspend fun invoke(id: Uuid): Result<Business> = transactionManager.transaction {
        businessDataSource.getBusinessById(id) ?: throw GetBusinessDetails.Error.NotFound()
    }
}
