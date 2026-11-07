package com.bookk.business.domain.impl.operation.erasure

import com.bookk.business.domain.api.erasure.operation.DeleteExpiredErasureRecords
import com.bookk.business.domain.datasource.ErasedClientDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

internal class DeleteExpiredErasureRecordsImpl(
    private val erasedClientDataSource: ErasedClientDataSource,
    private val transactionManager: TransactionManager,
) : DeleteExpiredErasureRecords {
    override suspend fun invoke(): Result<Unit> = transactionManager.transaction {
        erasedClientDataSource.deleteRecordedBefore(Clock.System.now() - RETENTION)
    }

    companion object {
        val RETENTION = 60.days
    }
}
