package com.bookk.business.domain.impl.operation.client

import com.bookk.business.domain.api.business.entity.BusinessResource
import com.bookk.business.domain.api.client.entity.ClientRemote
import com.bookk.business.domain.api.client.entity.toRemote
import com.bookk.business.domain.api.client.operation.GetClients
import com.bookk.business.domain.datasource.BusinessPermissionDataSource
import com.bookk.business.domain.datasource.ClientDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import library.permissions.PermissionAction
import library.permissions.assert
import kotlin.uuid.Uuid

internal class GetClientsImpl(
    private val clientsDataSource: ClientDataSource,
    private val businessPermissionDataSource: BusinessPermissionDataSource,
    private val transactionManager: TransactionManager
): GetClients {
    override suspend fun invoke(requestUserId: Uuid, businessId: Uuid): Result<List<ClientRemote>> =
        transactionManager.transaction {
            businessPermissionDataSource.getPermission(requestUserId, businessId, BusinessResource.CLIENTS)
                .assert(PermissionAction.VIEW)
            clientsDataSource.getClients(businessId).map { it.toRemote() }
        }
}
