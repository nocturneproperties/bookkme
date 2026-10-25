package com.bookk.business.domain.impl.operation.service

import com.bookk.business.domain.api.service.entity.ServiceGroup
import com.bookk.business.domain.api.service.operation.GetServiceGroups
import com.bookk.business.domain.datasource.ServiceDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import kotlin.uuid.Uuid

internal class GetServiceGroupsImpl(
    private val dataSource: ServiceDataSource,
    private val transactionManager: TransactionManager
) : GetServiceGroups {
    override suspend fun invoke(businessId: Uuid): Result<List<ServiceGroup>> = transactionManager.transaction {
        dataSource.getServiceGroups(businessId)
    }
}