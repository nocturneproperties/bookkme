package com.bookk.auth.domain.impl.operation

import com.bookk.auth.domain.api.delete_account.entity.DeletionReminder
import com.bookk.auth.domain.api.delete_account.entity.PendingAccountDeletion
import com.bookk.auth.domain.api.delete_account.operation.HandleScheduledAccountDeletions
import com.bookk.auth.domain.datasource.AccountDataSource
import com.bookk.auth.domain.datasource.AccountDeletionDataSource
import com.bookk.core.data.eventstreaming.StandardEventProducer
import com.bookk.core.data.eventstreaming.send
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.transactionOrThrow
import com.bookk.core.domain.entity.runSuspendCatching
import com.bookk.core.domain.entity.throwIfAnyFailed
import com.bookk.server.auth.client.AuthEvent
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

internal class HandleScheduledAccountDeletionsImpl(
    private val accountDataSource: AccountDataSource,
    private val accountDeletionDataSource: AccountDeletionDataSource,
    private val accountEraser: AccountEraser,
    private val eventProducer: StandardEventProducer,
    private val transactionManager: TransactionManager,
) : HandleScheduledAccountDeletions {

    override suspend fun invoke(): Result<Unit> = runSuspendCatching {
        val now = Clock.System.now()
        val candidates = transactionManager.transactionOrThrow {
            accountDeletionDataSource.getDueBy(now + FIRST_REMINDER_LEAD)
        }
        candidates.map { handle(it, now) }.throwIfAnyFailed()
    }

    private suspend fun handle(listed: PendingAccountDeletion, now: Instant): Result<Unit> = runSuspendCatching {
        if (listed.deletionHold) return@runSuspendCatching
        transactionManager.transactionOrThrow {
            if (!accountDataSource.lockAccount(listed.authId)) return@transactionOrThrow
            val locked = accountDeletionDataSource.get(listed.authId)
            if (locked == listed) advance(locked, now)
        }
    }

    private suspend fun advance(deletion: PendingAccountDeletion, now: Instant) {
        val oneDayReminderDue = now >= deletion.deletesAt - FINAL_REMINDER_LEAD
        val twoDayReminderDue = now >= deletion.deletesAt - FIRST_REMINDER_LEAD
        when {
            now >= deletion.deletesAt -> accountEraser.erase(deletion.authId, deletion.userId)

            oneDayReminderDue && deletion.reminder != DeletionReminder.ONE_DAY ->
                remind(deletion, DeletionReminder.ONE_DAY)

            twoDayReminderDue && deletion.reminder == DeletionReminder.NONE ->
                remind(deletion, DeletionReminder.TWO_DAYS)
        }
    }

    private suspend fun remind(deletion: PendingAccountDeletion, reminder: DeletionReminder) {
        eventProducer.send(AuthEvent.AccountDeletionScheduled(deletion.userId, deletion.deletesAt, reminder = true))
        accountDeletionDataSource.setReminder(deletion.authId, reminder)
    }

    companion object {
        val FIRST_REMINDER_LEAD = 2.days
        val FINAL_REMINDER_LEAD = 1.days
    }
}
