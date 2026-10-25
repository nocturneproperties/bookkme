package com.bookk.business.domain.impl.operation.service

import com.bookk.business.domain.api.business.entity.BusinessResource
import com.bookk.business.domain.api.service.entity.Service
import com.bookk.business.domain.api.service.operation.CreateService
import com.bookk.business.domain.api.service.operation.UpdateService
import com.bookk.business.domain.datasource.BusinessDataSource
import com.bookk.business.domain.datasource.BusinessPermissionDataSource
import com.bookk.business.domain.datasource.ServiceDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.entity.onConstraintFailure
import library.permissions.PermissionAction
import library.permissions.assert
import kotlin.uuid.Uuid

internal class UpdateServiceImpl(
    private val dataSource: ServiceDataSource,
    private val businessDataSource: BusinessDataSource,
    private val businessPermissionDataSource: BusinessPermissionDataSource,
    private val transactionManager: TransactionManager
): UpdateService {
    override suspend fun invoke(requestUserId: Uuid, service: Service): Result<Service> {
        if (service.name.isBlank()) return Result.failure(CreateService.Error.ValidationError())
        return transactionManager.transaction {
            val businessCurrency = businessDataSource.lockSupportedCurrencyOrNull(service.businessId)
            businessPermissionDataSource.getPermission(requestUserId, service.businessId, BusinessResource.SERVICES)
                .assert(PermissionAction.UPDATE)
            val currency = businessCurrency ?: throw UpdateService.Error.BusinessCurrencyInvalid()
            val price = service.price.inCurrencyOrNull(currency) ?: throw UpdateService.Error.PricePrecisionInvalid()
            val group = dataSource.getServiceGroup(service.group.id, service.businessId)
                ?: throw UpdateService.Error.ServiceGroupNotFound()
            dataSource.editService(service.copy(group = group, price = price))
        }.onConstraintFailure {
            throw UpdateService.Error.ServiceExist()
        }
    }
}