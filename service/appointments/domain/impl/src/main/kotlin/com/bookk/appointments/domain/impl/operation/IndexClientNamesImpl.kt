package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.api.operation.IndexClientNames
import com.bookk.appointments.domain.datasource.AppointmentDataSource
import com.bookk.core.domain.datasource.transaction.TransactionIsolation
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.transactionOrThrow
import com.bookk.core.domain.entity.runSuspendCatching
import kotlin.uuid.Uuid

private const val BATCH_SIZE = 100

internal class IndexClientNamesImpl(
    private val appointmentDataSource: AppointmentDataSource,
    private val transactionManager: TransactionManager,
) : IndexClientNames {

    override suspend fun invoke(): Result<Unit> = runSuspendCatching {
        var cursor: Uuid? = Uuid.NIL
        while (cursor != null) {
            cursor = indexBatchAfter(cursor)
        }
    }

    private suspend fun indexBatchAfter(
        cursor: Uuid,
    ): Uuid? = transactionManager.transactionOrThrow(TransactionIsolation.READ_COMMITTED) {
        appointmentDataSource.indexClientNames(cursor, BATCH_SIZE)
    }
}
