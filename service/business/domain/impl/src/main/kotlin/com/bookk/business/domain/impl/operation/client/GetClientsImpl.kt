package com.bookk.business.domain.impl.operation.client

import com.bookk.business.domain.api.business.entity.BusinessResource
import com.bookk.business.domain.api.client.entity.ClientPage
import com.bookk.business.domain.api.client.entity.toRemote
import com.bookk.business.domain.api.client.operation.GetClients
import com.bookk.business.domain.datasource.BusinessPermissionDataSource
import com.bookk.business.domain.datasource.ClientDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.entity.PageRequest
import library.permissions.PermissionAction
import library.permissions.assert
import kotlin.uuid.Uuid

internal class GetClientsImpl(
    private val clientsDataSource: ClientDataSource,
    private val businessPermissionDataSource: BusinessPermissionDataSource,
    private val transactionManager: TransactionManager,
) : GetClients {
    override suspend fun invoke(
        requestUserId: Uuid,
        businessId: Uuid,
        limit: Int?,
        cursor: String?,
        query: String?,
    ): Result<ClientPage> {
        val page = PageRequest.of(limit, cursor).getOrElse { return Result.failure(it) }
        return transactionManager.transaction {
            businessPermissionDataSource.getPermission(requestUserId, businessId, BusinessResource.CLIENTS)
                .assert(PermissionAction.VIEW)
            val clients = clientsDataSource.getClientsPage(businessId, page, query).map { it.toRemote() }
            ClientPage(data = clients.items, nextCursor = clients.nextCursor)
        }
    }
}
