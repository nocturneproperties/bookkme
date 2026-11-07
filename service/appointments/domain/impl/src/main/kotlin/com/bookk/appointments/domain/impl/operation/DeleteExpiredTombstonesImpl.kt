package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.api.operation.DeleteExpiredTombstones
import com.bookk.appointments.domain.datasource.DeletedClientDataSource
import com.bookk.appointments.domain.datasource.DeletedUserDataSource
import com.bookk.core.domain.datasource.transaction.TransactionIsolation
import com.bookk.core.domain.datasource.transaction.TransactionManager
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

internal class DeleteExpiredTombstonesImpl(
    private val deletedUserDataSource: DeletedUserDataSource,
    private val deletedClientDataSource: DeletedClientDataSource,
    private val transactionManager: TransactionManager,
) : DeleteExpiredTombstones {
    override suspend fun invoke(): Result<Unit> = transactionManager.transaction(TransactionIsolation.READ_COMMITTED) {
        val cutoff = Clock.System.now() - RETENTION
        deletedUserDataSource.deleteMarkedBefore(cutoff)
        deletedClientDataSource.deleteMarkedBefore(cutoff)
    }

    companion object {
        val RETENTION = 60.days
    }
}
