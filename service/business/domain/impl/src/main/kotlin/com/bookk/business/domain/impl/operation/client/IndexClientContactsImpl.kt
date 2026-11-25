package com.bookk.business.domain.impl.operation.client

import com.bookk.business.domain.api.client.operation.IndexClientContacts
import com.bookk.business.domain.datasource.ClientDataSource
import com.bookk.core.domain.datasource.transaction.TransactionIsolation
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.transactionOrThrow
import com.bookk.core.domain.entity.runSuspendCatching
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.uuid.Uuid

private const val BATCH_SIZE = 100

internal class IndexClientContactsImpl(
    private val clientDataSource: ClientDataSource,
    private val transactionManager: TransactionManager,
) : IndexClientContacts {

    private val finishedPass = AtomicBoolean(false)

    override suspend fun invoke(): Result<Unit> = runSuspendCatching {
        if (finishedPass.get()) return@runSuspendCatching
        var cursor: Uuid? = Uuid.NIL
        while (cursor != null) {
            cursor = indexBatchAfter(cursor)
        }
        finishedPass.set(true)
    }

    private suspend fun indexBatchAfter(
        cursor: Uuid,
    ): Uuid? = transactionManager.transactionOrThrow(TransactionIsolation.READ_COMMITTED) {
        clientDataSource.indexClientContacts(cursor, BATCH_SIZE)
    }
}
