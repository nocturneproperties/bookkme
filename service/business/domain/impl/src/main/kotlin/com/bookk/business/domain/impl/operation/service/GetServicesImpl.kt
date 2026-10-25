package com.bookk.business.domain.impl.operation.service

import com.bookk.business.domain.api.service.entity.Service
import com.bookk.business.domain.api.service.operation.GetServices
import com.bookk.business.domain.datasource.ServiceDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import kotlin.uuid.Uuid

internal class GetServicesImpl(
    private val dataSource: ServiceDataSource,
    private val transactionManager: TransactionManager
) : GetServices {
    override suspend fun invoke(businessId: Uuid): Result<List<Service>> = transactionManager.transaction {
        dataSource.getServices(businessId)
    }
}