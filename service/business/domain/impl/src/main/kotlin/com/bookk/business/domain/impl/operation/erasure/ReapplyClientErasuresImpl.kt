package com.bookk.business.domain.impl.operation.erasure

import com.bookk.business.domain.api.client.entity.ErasedClient
import com.bookk.business.domain.api.erasure.operation.ReapplyClientErasures
import com.bookk.business.domain.datasource.ClientDataSource
import com.bookk.business.domain.datasource.ErasedClientDataSource
import com.bookk.core.data.eventstreaming.StandardEventProducer
import com.bookk.core.data.eventstreaming.send
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.transactionOrThrow
import com.bookk.core.domain.entity.runSuspendCatching
import com.bookk.core.domain.entity.throwIfAnyFailed
import com.bookk.server.business.client.api.event.BusinessEvent
import library.encryption.SubjectKeys
import library.export.DataExports

internal class ReapplyClientErasuresImpl(
    private val erasedClientDataSource: ErasedClientDataSource,
    private val clientDataSource: ClientDataSource,
    private val eventProducer: StandardEventProducer,
    private val transactionManager: TransactionManager,
    private val dataExports: DataExports,
    private val subjectKeys: SubjectKeys,
) : ReapplyClientErasures {
    override suspend fun invoke(): Result<Unit> = runSuspendCatching {
        val requested = transactionManager.transactionOrThrow { erasedClientDataSource.getReapplyRequested() }
        requested.map { reapply(it) }.throwIfAnyFailed()
    }

    private suspend fun reapply(client: ErasedClient): Result<Unit> = transactionManager.transaction {
        if (!erasedClientDataSource.claimReapply(client.clientId)) return@transaction
        clientDataSource.deleteClient(client.businessId, client.clientId)
        dataExports.discard(client.businessId)
        eventProducer.send(BusinessEvent.ClientDeleted(businessId = client.businessId, clientId = client.clientId))
        subjectKeys.shred(client.clientId)
    }
}
