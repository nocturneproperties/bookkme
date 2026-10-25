package com.bookk.business.domain.impl.operation.client

import com.bookk.business.domain.api.business.entity.BusinessResource
import com.bookk.business.domain.api.client.operation.DeleteClient
import com.bookk.business.domain.datasource.BusinessPermissionDataSource
import com.bookk.business.domain.datasource.ClientDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import library.permissions.PermissionAction
import library.permissions.assert
import kotlin.uuid.Uuid

internal class DeleteClientImpl(
    private val transactionManager: TransactionManager,
    private val clientDataSource: ClientDataSource,
    private val businessPermissionDataSource: BusinessPermissionDataSource
) : DeleteClient {
    override suspend fun invoke(requestUserId: Uuid, businessId: Uuid, id: Uuid): Result<Unit> =
        transactionManager.transaction {
            businessPermissionDataSource.getPermission(requestUserId, businessId, BusinessResource.CLIENTS)
                .assert(PermissionAction.DELETE)
            if (!clientDataSource.deleteClient(businessId, id)) {
                throw DeleteClient.Error.NotFound()
            }
        }
}
