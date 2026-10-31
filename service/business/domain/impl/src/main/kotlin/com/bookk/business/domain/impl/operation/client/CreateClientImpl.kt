package com.bookk.business.domain.impl.operation.client

import com.bookk.business.domain.api.business.entity.BusinessResource
import com.bookk.business.domain.api.client.entity.Client
import com.bookk.business.domain.api.client.entity.ClientRemote
import com.bookk.business.domain.api.client.entity.toRemote
import com.bookk.business.domain.api.client.operation.CreateClient
import com.bookk.business.domain.datasource.BusinessPermissionDataSource
import com.bookk.business.domain.datasource.ClientDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.entity.onConstraintFailure
import library.permissions.PermissionAction
import library.permissions.assert
import kotlin.uuid.Uuid

internal class CreateClientImpl(
    private val transactionManager: TransactionManager,
    private val clientDataSource: ClientDataSource,
    private val businessPermissionDataSource: BusinessPermissionDataSource
) : CreateClient {
    override suspend fun invoke(requestUserId: Uuid, businessId: Uuid, client: Client.Detached): Result<ClientRemote> =
        transactionManager.transaction {
            businessPermissionDataSource.getPermission(requestUserId, businessId, BusinessResource.CLIENTS)
                .assert(PermissionAction.UPDATE)
            val fields = client.validatedFields()
            clientDataSource.createDetachedClient(
                businessId,
                client.copy(
                    name = fields.name,
                    lastName = fields.lastName,
                    phone = fields.phone,
                    email = fields.email,
                    description = fields.description
                )
            ).toRemote()
        }.onConstraintFailure { throw CreateClient.Error.ClientExist() }
}
