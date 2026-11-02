package com.bookk.auth.domain.impl.operation.authentication

import com.bookk.auth.domain.api.authentication.entity.DormancyStage
import com.bookk.auth.domain.api.authentication.entity.DormantAccount
import com.bookk.auth.domain.api.authentication.operation.HandleDormantAccounts
import com.bookk.auth.domain.datasource.AccountDataSource
import com.bookk.auth.domain.impl.operation.AccountEraser
import com.bookk.core.data.eventstreaming.StandardEventProducer
import com.bookk.core.data.eventstreaming.send
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.entity.runSuspendCatching
import com.bookk.server.auth.client.AuthEvent
import com.bookk.server.business.client.api.BusinessClient
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class HandleDormantAccountsImpl(
    private val accountDataSource: AccountDataSource,
    private val businessClient: BusinessClient,
    private val accountEraser: AccountEraser,
    private val eventProducer: StandardEventProducer,
    private val transactionManager: TransactionManager,
) : HandleDormantAccounts {
    override suspend fun invoke(): Result<Unit> = runSuspendCatching {
        val now = Clock.System.now()
        val activeSince = now.minus(INACTIVITY_MONTHS, DateTimeUnit.MONTH, TimeZone.UTC)
        val candidates = transactionManager.transaction {
            accountDataSource.getDormantAccounts(activeSince + FIRST_WARNING_LEAD)
        }.getOrThrow()
        val failures = mutableListOf<Throwable>()
        for (candidate in candidates) {
            handle(candidate, now, activeSince)
                .onFailure { failures.add(it) }
        }

        failures
            .reduceOrNull { acc, throwable ->
                acc.apply { addSuppressed(throwable) }
            }
            ?.let { throw it }
    }

    private suspend fun handle(
        listed: DormantAccount,
        now: Instant,
        activeSince: Instant,
    ): Result<Unit> = runSuspendCatching {
        if (ownsActiveBusiness(listed.userId, activeSince)) return@runSuspendCatching
        transactionManager.transaction {
            val locked = accountDataSource.getDormantAccountForUpdate(listed.userId)
            if (locked == listed) advance(locked, now)
        }.getOrThrow()
    }

    private suspend fun ownsActiveBusiness(userId: Uuid, activeSince: Instant): Boolean {
        val members = businessClient.getOwnedBusinessMemberIds(userId).getOrThrow()
        if (members.isEmpty()) return false
        val lastActivity = transactionManager.transaction {
            accountDataSource.getLastActiveAt(members)
        }.getOrThrow()
        return lastActivity.values.any { it >= activeSince }
    }

    private suspend fun advance(account: DormantAccount, now: Instant) {
        val firstWarnedAt = account.warnedAt ?: now
        val deletesAt = firstWarnedAt + FIRST_WARNING_LEAD
        when (account.stage) {
            DormancyStage.ACTIVE -> {
                warn(account.userId, deletesAt, final = false)
                accountDataSource.setDormancyStage(account.userId, DormancyStage.SENT_60_DAY_WARNING, now)
            }

            DormancyStage.SENT_60_DAY_WARNING -> if (now >= deletesAt - SECOND_WARNING_LEAD) {
                warn(account.userId, deletesAt, final = false)
                accountDataSource.setDormancyStage(account.userId, DormancyStage.SENT_30_DAY_WARNING, firstWarnedAt)
            }

            DormancyStage.SENT_30_DAY_WARNING -> if (now >= deletesAt - FINAL_WARNING_LEAD) {
                warn(account.userId, deletesAt, final = true)
                accountDataSource.setDormancyStage(account.userId, DormancyStage.SENT_7_DAY_WARNING, firstWarnedAt)
            }

            DormancyStage.SENT_7_DAY_WARNING -> if (now >= deletesAt) {
                accountEraser.erase(account.authId, account.userId)
            }
        }
    }

    private suspend fun warn(userId: Uuid, deletesAt: Instant, final: Boolean) {
        eventProducer.send(AuthEvent.AccountDormancyWarning(userId = userId, deletesAt = deletesAt, final = final))
    }

    companion object {
        const val INACTIVITY_MONTHS = 36
        val FIRST_WARNING_LEAD = 60.days
        val SECOND_WARNING_LEAD = 30.days
        val FINAL_WARNING_LEAD = 7.days
    }
}
