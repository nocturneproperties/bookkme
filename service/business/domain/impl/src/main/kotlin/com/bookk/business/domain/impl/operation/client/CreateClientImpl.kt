package com.bookk.business.domain.impl.operation.client

import com.bookk.business.domain.api.business.entity.BusinessResource
import com.bookk.business.domain.api.client.entity.Client
import com.bookk.business.domain.api.client.entity.ClientRemote
import com.bookk.business.domain.api.client.entity.toRemote
import com.bookk.business.domain.api.client.operation.CreateClient
import com.bookk.business.domain.datasource.BusinessDataSource
import com.bookk.business.domain.datasource.BusinessPermissionDataSource
import com.bookk.business.domain.datasource.ClientDataSource
import com.bookk.business.domain.datasource.RowLock
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.entity.Error
import com.bookk.core.domain.entity.onConstraintFailure
import library.permissions.PermissionAction
import library.permissions.assert
import kotlin.uuid.Uuid

internal class CreateClientImpl(
    private val transactionManager: TransactionManager,
    private val clientDataSource: ClientDataSource,
    private val businessPermissionDataSource: BusinessPermissionDataSource,
    private val businessDataSource: BusinessDataSource,
) : CreateClient {
    override suspend fun invoke(
        requestUserId: Uuid,
        businessId: Uuid,
        client: Client.Detached,
    ): Result<ClientRemote> = transactionManager.transaction {
        val locked = businessDataSource.lockBusiness(businessId, RowLock.EXCLUSIVE) ?: throw Error.NotFound()
        businessPermissionDataSource.getPermission(requestUserId, businessId, BusinessResource.CLIENTS)
            .assert(PermissionAction.UPDATE)
        val fields = client.validatedFields()
        if (clientDataSource.countClients(businessId) >= locked.limits.maxClients) {
            throw CreateClient.Error.ClientsLimitReached()
        }
        clientDataSource.createDetachedClient(
            businessId,
            client.copy(
                name = fields.name,
                lastName = fields.lastName,
                phone = fields.phone,
                email = fields.email,
                description = fields.description,
            ),
        ).toRemote()
    }.onConstraintFailure { throw CreateClient.Error.ClientExist() }
}
