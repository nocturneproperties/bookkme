package com.bookk.business.domain.impl.operation.client

import com.bookk.business.domain.api.client.operation.DeleteInactiveClients
import com.bookk.business.domain.datasource.BusinessDataSource
import com.bookk.business.domain.datasource.ClientDataSource
import com.bookk.business.domain.datasource.ErasedClientDataSource
import com.bookk.core.data.eventstreaming.StandardEventProducer
import com.bookk.core.data.eventstreaming.send
import com.bookk.core.domain.datasource.transaction.TransactionIsolation
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.transactionOrThrow
import com.bookk.core.domain.entity.runSuspendCatching
import com.bookk.core.domain.entity.throwIfAnyFailed
import com.bookk.server.business.client.api.event.BusinessEvent
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import library.encryption.SubjectKeys
import library.export.DataExports
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class DeleteInactiveClientsImpl(
    private val businessDataSource: BusinessDataSource,
    private val clientDataSource: ClientDataSource,
    private val erasedClientDataSource: ErasedClientDataSource,
    private val transactionManager: TransactionManager,
    private val eventProducer: StandardEventProducer,
    private val dataExports: DataExports,
    private val subjectKeys: SubjectKeys,
) : DeleteInactiveClients {
    override suspend fun invoke(): Result<Unit> = runSuspendCatching {
        val retentionMonthsByBusiness = transactionManager.transactionOrThrow {
            businessDataSource.getClientDataRetentionMonths()
        }
        val now = Clock.System.now()
        retentionMonthsByBusiness
            .map { (businessId, retentionMonths) ->
                deleteInactiveClients(businessId, now.minus(retentionMonths, DateTimeUnit.MONTH, TimeZone.UTC))
            }
            .throwIfAnyFailed()
    }

    private suspend fun deleteInactiveClients(
        businessId: Uuid,
        inactiveSince: Instant,
    ): Result<Unit> = transactionManager.transaction(TransactionIsolation.READ_COMMITTED) {
        val inactiveIds = clientDataSource.getInactiveClientIdsForUpdate(businessId, inactiveSince)
        if (inactiveIds.isEmpty()) return@transaction
        clientDataSource.deleteClients(inactiveIds)
        erasedClientDataSource.recordAll(businessId, inactiveIds)
        inactiveIds.forEach { clientId ->
            eventProducer.send(BusinessEvent.ClientDeleted(businessId = businessId, clientId = clientId))
        }
        dataExports.discard(businessId)
        subjectKeys.shredAll(inactiveIds)
    }
}
