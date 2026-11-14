package com.bookk.user.domain.impl.operation

import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.transactionOrThrow
import com.bookk.core.domain.entity.runSuspendCatching
import com.bookk.user.domain.api.operation.EncryptPlaintextProfiles
import com.bookk.user.domain.datasource.UserDataSource

private const val BATCH_SIZE = 100

internal class EncryptPlaintextProfilesImpl(
    private val userDataSource: UserDataSource,
    private val transactionManager: TransactionManager,
) : EncryptPlaintextProfiles {

    override suspend fun invoke(): Result<Unit> = runSuspendCatching {
        do {
            val encrypted = transactionManager.transactionOrThrow {
                userDataSource.encryptPlaintextProfiles(BATCH_SIZE)
            }
        } while (encrypted == BATCH_SIZE)
    }
}
