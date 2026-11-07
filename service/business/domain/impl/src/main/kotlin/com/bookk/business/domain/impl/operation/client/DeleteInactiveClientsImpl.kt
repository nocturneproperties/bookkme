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
import com.bookk.server.business.client.api.event.BusinessEvent
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class DeleteInactiveClientsImpl(
    private val businessDataSource: BusinessDataSource,
    private val clientDataSource: ClientDataSource,
    private val erasedClientDataSource: ErasedClientDataSource,
    private val transactionManager: TransactionManager,
    private val eventProducer: StandardEventProducer,
) : DeleteInactiveClients {
    override suspend fun invoke(): Result<Unit> = runSuspendCatching {
        val retentionMonthsByBusiness = transactionManager.transactionOrThrow { businessDataSource.getClientDataRetentionMonths() }
        val now = Clock.System.now()
        val failures = mutableListOf<Throwable>()
        for ((businessId, retentionMonths) in retentionMonthsByBusiness) {
            deleteInactiveClients(
                businessId,
                inactiveSince = now.minus(retentionMonths, DateTimeUnit.MONTH, TimeZone.UTC),
            ).onFailure { failures.add(it) }
        }

        failures
            .reduceOrNull { acc, throwable ->
                acc.apply { addSuppressed(throwable) }
            }
            ?.let { throw it }
    }

    private suspend fun deleteInactiveClients(businessId: Uuid, inactiveSince: Instant): Result<Unit> = transactionManager.transaction(TransactionIsolation.READ_COMMITTED) {
        clientDataSource.deleteInactiveClients(businessId, inactiveSince).forEach { clientId ->
            erasedClientDataSource.record(businessId, clientId)
            eventProducer.send(BusinessEvent.ClientDeleted(businessId = businessId, clientId = clientId))
        }
    }
}
