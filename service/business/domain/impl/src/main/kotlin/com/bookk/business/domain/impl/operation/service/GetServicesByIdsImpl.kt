package com.bookk.business.domain.impl.operation.service

import com.bookk.business.domain.api.service.entity.Service
import com.bookk.business.domain.api.service.operation.GetServicesByIds
import com.bookk.business.domain.datasource.ServiceDataSource
import com.bookk.business.domain.impl.operation.getServicesExpanded
import com.bookk.core.domain.datasource.transaction.TransactionManager
import kotlin.uuid.Uuid

internal class GetServicesByIdsImpl(
    private val serviceDataSource: ServiceDataSource,
    private val transactionManager: TransactionManager
) : GetServicesByIds {
    override suspend fun invoke(businessId: Uuid, serviceIds: List<Uuid>): Result<List<Service>> {
        if (serviceIds.isEmpty()) return Result.success(emptyList())
        return transactionManager.transaction {
            serviceDataSource.getServicesExpanded(businessId, serviceIds) ?: throw GetServicesByIds.Error.ServiceNotFound()
        }
    }
}
