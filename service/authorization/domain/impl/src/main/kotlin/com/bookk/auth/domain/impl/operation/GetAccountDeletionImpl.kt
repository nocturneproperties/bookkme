package com.bookk.auth.domain.impl.operation

import com.bookk.auth.domain.api.delete_account.entity.AccountDeletionStatus
import com.bookk.auth.domain.api.delete_account.operation.GetAccountDeletion
import com.bookk.auth.domain.datasource.AccountDeletionDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import kotlin.uuid.Uuid

internal class GetAccountDeletionImpl(
    private val accountDeletionDataSource: AccountDeletionDataSource,
    private val transactionManager: TransactionManager,
) : GetAccountDeletion {

    override suspend fun invoke(userId: Uuid): Result<AccountDeletionStatus> = transactionManager.transaction {
        AccountDeletionStatus(accountDeletionDataSource.getByUserId(userId)?.deletesAt)
    }
}
