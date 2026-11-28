package com.bookk.auth.domain.impl.operation

import com.bookk.auth.domain.api.delete_account.entity.AccountDeletion
import com.bookk.auth.domain.api.delete_account.operation.DeleteAccount
import com.bookk.auth.domain.api.delete_account.operation.DeleteAccount.Error.InvalidCredentials
import com.bookk.auth.domain.datasource.AccountDataSource
import com.bookk.auth.domain.datasource.AccountDeletionDataSource
import com.bookk.core.data.eventstreaming.StandardEventProducer
import com.bookk.core.data.eventstreaming.send
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.transactionOrThrow
import com.bookk.core.domain.entity.runSuspendCatching
import com.bookk.server.auth.client.AuthEvent
import library.actionconfirmation.ActionConfirmationGate
import library.actionconfirmation.ActionConfirmationGrant
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.uuid.Uuid

internal class DeleteAccountImpl(
    private val actionConfirmationGate: ActionConfirmationGate,
    private val accountDataSource: AccountDataSource,
    private val accountDeletionDataSource: AccountDeletionDataSource,
    private val eventProducer: StandardEventProducer,
    private val transactionManager: TransactionManager,
) : DeleteAccount {

    override suspend fun invoke(
        userId: Uuid,
        confirmationToken: String,
    ): Result<AccountDeletion> = runSuspendCatching {
        actionConfirmationGate.spend(
            confirmationToken,
            ActionConfirmationGrant.accountDeletion(userId),
            DeleteAccount.Error::ActionConfirmationInvalid,
        ) {
            transactionManager.transactionOrThrow {
                val account = accountDataSource.lockAccountOfUser(userId) ?: throw InvalidCredentials()
                if (account.deletionHold) throw DeleteAccount.Error.DeletionOnHold()
                val pending = accountDeletionDataSource.get(account.id)
                if (pending != null) return@transactionOrThrow AccountDeletion(pending.deletesAt)
                val requestedAt = Clock.System.now()
                val deletesAt = requestedAt + GRACE_PERIOD
                accountDeletionDataSource.schedule(account.id, requestedAt, deletesAt)
                eventProducer.send(AuthEvent.AccountDeletionScheduled(userId, deletesAt, reminder = false))
                AccountDeletion(deletesAt)
            }
        }
    }

    companion object {
        val GRACE_PERIOD = 7.days
    }
}
