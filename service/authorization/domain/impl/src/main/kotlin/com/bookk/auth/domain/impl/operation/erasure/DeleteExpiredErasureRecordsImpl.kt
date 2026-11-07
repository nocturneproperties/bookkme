package com.bookk.auth.domain.impl.operation.erasure

import com.bookk.auth.domain.api.erasure.operation.DeleteExpiredErasureRecords
import com.bookk.auth.domain.datasource.ErasedAccountDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

internal class DeleteExpiredErasureRecordsImpl(
    private val erasedAccountDataSource: ErasedAccountDataSource,
    private val transactionManager: TransactionManager,
) : DeleteExpiredErasureRecords {
    override suspend fun invoke(): Result<Unit> = transactionManager.transaction {
        erasedAccountDataSource.deleteRecordedBefore(Clock.System.now() - RETENTION)
    }

    companion object {
        val RETENTION = 60.days
    }
}
