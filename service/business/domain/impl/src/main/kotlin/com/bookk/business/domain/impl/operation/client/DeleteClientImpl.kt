package com.bookk.business.domain.impl.operation.client

import com.bookk.business.domain.api.business.entity.BusinessResource
import com.bookk.business.domain.api.client.entity.Client
import com.bookk.business.domain.api.client.operation.DeleteClient
import com.bookk.business.domain.datasource.BusinessDataSource
import com.bookk.business.domain.datasource.BusinessPermissionDataSource
import com.bookk.business.domain.datasource.ClientDataSource
import com.bookk.business.domain.datasource.ErasedClientDataSource
import com.bookk.business.domain.datasource.StepUpDataSource
import com.bookk.business.domain.impl.stepup.StepUpVerifier
import com.bookk.core.data.eventstreaming.StandardEventProducer
import com.bookk.core.data.eventstreaming.send
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.entity.Error
import com.bookk.core.domain.entity.runCleaningUpOnFailure
import com.bookk.core.domain.entity.runSuspendCatching
import com.bookk.server.auth.client.StepUpClaims
import com.bookk.server.business.client.api.event.BusinessEvent
import library.permissions.PermissionAction
import library.permissions.assert
import org.slf4j.LoggerFactory
import kotlin.uuid.Uuid

private val deleteClientLogger = LoggerFactory.getLogger(DeleteClientImpl::class.java)

internal class DeleteClientImpl(
    private val transactionManager: TransactionManager,
    private val clientDataSource: ClientDataSource,
    private val businessPermissionDataSource: BusinessPermissionDataSource,
    private val businessDataSource: BusinessDataSource,
    private val eventProducer: StandardEventProducer,
    private val stepUpVerifier: StepUpVerifier,
    private val stepUpDataSource: StepUpDataSource,
    private val erasedClientDataSource: ErasedClientDataSource,
) : DeleteClient {
    override suspend fun invoke(requestUserId: Uuid, businessId: Uuid, id: Uuid, stepUpToken: String): Result<Unit> = runSuspendCatching {
        val proof = stepUpVerifier.verify(
            token = stepUpToken,
            userId = requestUserId,
            action = StepUpClaims.ACTION_DELETE_CLIENT,
            businessId = businessId,
            targetId = id,
        ) ?: throw DeleteClient.Error.StepUpInvalid()

        if (!stepUpDataSource.reserve(proof.tokenId, proof.remainingLifetime)) throw DeleteClient.Error.StepUpInvalid()

        runCleaningUpOnFailure(cleanUp = { releaseBestEffort(proof.tokenId) }) {
            deleteClient(requestUserId, businessId, id).getOrThrow()
        }
    }

    private suspend fun deleteClient(requestUserId: Uuid, businessId: Uuid, id: Uuid): Result<Unit> = transactionManager.transaction {
        val client = clientDataSource.getClientForUpdate(businessId, id)
        businessPermissionDataSource.getPermission(requestUserId, businessId, BusinessResource.CLIENTS)
            .assert(PermissionAction.DELETE)
        if (client == null) throw DeleteClient.Error.NotFound()
        if (client is Client.Integrated && !businessDataSource.isOwner(requestUserId, businessId)) throw Error.OperationNotAllowed()
        if (!clientDataSource.deleteClient(businessId, id)) throw DeleteClient.Error.NotFound()
        erasedClientDataSource.record(businessId, id)
        eventProducer.send(BusinessEvent.ClientDeleted(businessId = businessId, clientId = id))
    }

    private suspend fun releaseBestEffort(tokenId: String) {
        runSuspendCatching { stepUpDataSource.release(tokenId) }
            .onFailure { deleteClientLogger.error("Failed to release step-up token $tokenId: ${it.message}") }
    }
}
