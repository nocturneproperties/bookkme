package com.bookk.business.domain.impl.operation.erasure

import com.bookk.business.domain.api.client.entity.ErasedClient
import com.bookk.business.domain.api.erasure.operation.ReapplyClientErasures
import com.bookk.business.domain.datasource.ClientDataSource
import com.bookk.business.domain.datasource.ErasedClientDataSource
import com.bookk.core.data.eventstreaming.StandardEventProducer
import com.bookk.core.data.eventstreaming.send
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.entity.runSuspendCatching
import com.bookk.server.business.client.api.event.BusinessEvent

internal class ReapplyClientErasuresImpl(
    private val erasedClientDataSource: ErasedClientDataSource,
    private val clientDataSource: ClientDataSource,
    private val eventProducer: StandardEventProducer,
    private val transactionManager: TransactionManager,
) : ReapplyClientErasures {
    override suspend fun invoke(): Result<Unit> = runSuspendCatching {
        val requested = transactionManager.transaction { erasedClientDataSource.getReapplyRequested() }.getOrThrow()
        requested
            .mapNotNull { client -> reapply(client).exceptionOrNull() }
            .reduceOrNull { first, next -> first.apply { addSuppressed(next) } }
            ?.let { throw it }
    }

    private suspend fun reapply(client: ErasedClient): Result<Unit> = transactionManager.transaction {
        if (!erasedClientDataSource.claimReapply(client.clientId)) return@transaction
        clientDataSource.deleteClient(client.businessId, client.clientId)
        eventProducer.send(BusinessEvent.ClientDeleted(businessId = client.businessId, clientId = client.clientId))
    }
}
