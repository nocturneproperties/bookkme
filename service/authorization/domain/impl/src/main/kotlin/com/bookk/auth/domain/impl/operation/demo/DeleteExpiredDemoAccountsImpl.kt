package com.bookk.auth.domain.impl.operation.demo

import com.bookk.auth.domain.api.authentication.entity.DemoAccount
import com.bookk.auth.domain.api.demo.entity.DemoAccountPolicy
import com.bookk.auth.domain.api.demo.operation.DeleteExpiredDemoAccounts
import com.bookk.auth.domain.datasource.AccountDataSource
import com.bookk.auth.domain.impl.operation.AccountEraser
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.transactionOrThrow
import com.bookk.core.domain.entity.runSuspendCatching
import com.bookk.core.domain.entity.throwIfAnyFailed
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import kotlin.time.Clock
import kotlin.time.Instant

internal class DeleteExpiredDemoAccountsImpl(
    private val accountDataSource: AccountDataSource,
    private val accountEraser: AccountEraser,
    private val transactionManager: TransactionManager,
    meterRegistry: MeterRegistry,
) : DeleteExpiredDemoAccounts {

    private val erasedDemos = Counter.builder(DEMO_ERASED_METRIC)
        .description("Demo accounts erased after their upgrade grace period")
        .register(meterRegistry)

    override suspend fun invoke(): Result<Unit> = runSuspendCatching {
        val graceEnded = Clock.System.now() - DemoAccountPolicy.UPGRADE_GRACE_PERIOD
        val expired = transactionManager.transactionOrThrow {
            accountDataSource.getDemoAccountsExpiredBefore(graceEnded)
        }
        expired.filterNot { it.deletionHold }
            .map { erase(it, graceEnded) }
            .throwIfAnyFailed()
    }

    private suspend fun erase(listed: DemoAccount, graceEnded: Instant): Result<Unit> = runSuspendCatching {
        val erased = transactionManager.transactionOrThrow {
            val locked = accountDataSource.getDemoAccountForUpdate(listed.authId)
            val stillExpired = locked != null && !locked.deletionHold && locked.expiresAt < graceEnded
            if (stillExpired) accountEraser.erase(locked.authId, locked.userId)
            stillExpired
        }
        if (erased) erasedDemos.increment()
    }

    companion object {
        const val DEMO_ERASED_METRIC = "auth.demo.erased"
    }
}
