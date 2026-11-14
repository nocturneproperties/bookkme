package com.bookk.notifications.domain.impl

import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.transactionOrThrow
import com.bookk.core.domain.entity.runSuspendCatching
import com.bookk.notifications.domain.api.EncryptPlaintextTargets
import com.bookk.notifications.domain.datasource.NotificationTargetDataSource

private const val BATCH_SIZE = 100

internal class EncryptPlaintextTargetsImpl(
    private val targetDataSource: NotificationTargetDataSource,
    private val transactionManager: TransactionManager,
) : EncryptPlaintextTargets {

    override suspend fun invoke(): Result<Unit> = runSuspendCatching {
        encryptInBatches { targetDataSource.encryptPlaintextEmails(BATCH_SIZE) }
        encryptInBatches { targetDataSource.encryptPlaintextTelegramTags(BATCH_SIZE) }
    }

    private suspend fun encryptInBatches(encryptBatch: suspend () -> Int) {
        do {
            val encrypted = transactionManager.transactionOrThrow { encryptBatch() }
        } while (encrypted == BATCH_SIZE)
    }
}
