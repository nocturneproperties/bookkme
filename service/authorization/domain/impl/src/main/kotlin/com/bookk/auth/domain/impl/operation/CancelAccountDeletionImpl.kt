package com.bookk.auth.domain.impl.operation

import com.bookk.auth.domain.api.delete_account.operation.CancelAccountDeletion
import com.bookk.auth.domain.datasource.AccountDataSource
import com.bookk.auth.domain.datasource.AccountDeletionDataSource
import com.bookk.core.data.eventstreaming.StandardEventProducer
import com.bookk.core.data.eventstreaming.send
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.server.auth.client.AuthEvent
import kotlin.uuid.Uuid

internal class CancelAccountDeletionImpl(
    private val accountDataSource: AccountDataSource,
    private val accountDeletionDataSource: AccountDeletionDataSource,
    private val eventProducer: StandardEventProducer,
    private val transactionManager: TransactionManager,
) : CancelAccountDeletion {

    override suspend fun invoke(userId: Uuid): Result<Unit> = transactionManager.transaction {
        val account = accountDataSource.lockAccountOfUser(userId) ?: return@transaction
        if (accountDeletionDataSource.cancel(account.id)) {
            eventProducer.send(AuthEvent.AccountDeletionCancelled(userId))
        }
    }
}
