package com.bookk.business.domain.impl.operation.client

import com.bookk.business.domain.api.business.entity.BusinessResource
import com.bookk.business.domain.api.client.entity.Client
import com.bookk.business.domain.api.client.operation.DeleteClient
import com.bookk.business.domain.datasource.BusinessDataSource
import com.bookk.business.domain.datasource.BusinessPermissionDataSource
import com.bookk.business.domain.datasource.ClientDataSource
import com.bookk.business.domain.datasource.ErasedClientDataSource
import com.bookk.core.data.eventstreaming.StandardEventProducer
import com.bookk.core.data.eventstreaming.send
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.entity.Error
import com.bookk.core.domain.entity.runSuspendCatching
import com.bookk.server.business.client.api.event.BusinessEvent
import library.actionconfirmation.ActionConfirmationGate
import library.actionconfirmation.ActionConfirmationGrant
import library.encryption.SubjectKeys
import library.export.DataExports
import library.permissions.PermissionAction
import library.permissions.assert
import kotlin.uuid.Uuid

internal class DeleteClientImpl(
    private val transactionManager: TransactionManager,
    private val clientDataSource: ClientDataSource,
    private val businessPermissionDataSource: BusinessPermissionDataSource,
    private val businessDataSource: BusinessDataSource,
    private val eventProducer: StandardEventProducer,
    private val actionConfirmationGate: ActionConfirmationGate,
    private val erasedClientDataSource: ErasedClientDataSource,
    private val dataExports: DataExports,
    private val subjectKeys: SubjectKeys,
) : DeleteClient {
    override suspend fun invoke(
        requestUserId: Uuid,
        businessId: Uuid,
        id: Uuid,
        confirmationToken: String,
    ): Result<Unit> = runSuspendCatching {
        actionConfirmationGate.spend(
            confirmationToken,
            ActionConfirmationGrant.clientDeletion(requestUserId, businessId, id),
            DeleteClient.Error::ActionConfirmationInvalid,
        ) {
            deleteClient(requestUserId, businessId, id).getOrThrow()
        }
    }

    private suspend fun deleteClient(
        requestUserId: Uuid,
        businessId: Uuid,
        id: Uuid,
    ): Result<Unit> = transactionManager.transaction {
        val client = clientDataSource.getClientForUpdate(businessId, id)
        businessPermissionDataSource.getPermission(requestUserId, businessId, BusinessResource.CLIENTS)
            .assert(PermissionAction.DELETE)
        if (client == null) throw DeleteClient.Error.NotFound()
        if (client is Client.Integrated && !businessDataSource.isOwner(requestUserId, businessId)) {
            throw Error.OperationNotAllowed()
        }
        if (!clientDataSource.deleteClient(businessId, id)) throw DeleteClient.Error.NotFound()
        erasedClientDataSource.record(businessId, id)
        dataExports.discard(businessId)
        eventProducer.send(BusinessEvent.ClientDeleted(businessId = businessId, clientId = id))
        subjectKeys.shred(id)
    }
}
