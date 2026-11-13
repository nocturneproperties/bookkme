package com.bookk.business.domain.impl.operation.business

import com.bookk.business.domain.api.business.operation.DeleteBusiness
import com.bookk.business.domain.datasource.BusinessDataSource
import com.bookk.core.data.eventstreaming.StandardEventProducer
import com.bookk.core.data.eventstreaming.send
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.server.business.client.api.event.BusinessEvent
import library.export.DataExports
import kotlin.uuid.Uuid

internal class DeleteBusinessImpl(
    private val businessDataSource: BusinessDataSource,
    private val transactionManager: TransactionManager,
    private val eventProducer: StandardEventProducer,
    private val dataExports: DataExports,
) : DeleteBusiness {
    override suspend fun invoke(userId: Uuid): Result<Unit> = transactionManager.transaction {
        val deletedBusinessIds = businessDataSource.deleteUserBusinesses(userId)
        if (deletedBusinessIds.isNotEmpty()) dataExports.discardAll(deletedBusinessIds)
        deletedBusinessIds.forEach { eventProducer.send(BusinessEvent.Deleted(it)) }
    }
}
