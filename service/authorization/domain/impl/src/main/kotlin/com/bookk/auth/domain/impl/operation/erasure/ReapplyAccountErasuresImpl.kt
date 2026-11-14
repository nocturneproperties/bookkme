package com.bookk.auth.domain.impl.operation.erasure

import com.bookk.auth.domain.api.erasure.operation.ReapplyAccountErasures
import com.bookk.auth.domain.datasource.AccountDataSource
import com.bookk.auth.domain.datasource.ErasedAccountDataSource
import com.bookk.auth.domain.impl.operation.AccountEraser
import com.bookk.core.data.eventstreaming.StandardEventProducer
import com.bookk.core.data.eventstreaming.send
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.transactionOrThrow
import com.bookk.core.domain.entity.runSuspendCatching
import com.bookk.core.domain.entity.throwIfAnyFailed
import com.bookk.server.auth.client.AuthEvent
import kotlin.uuid.Uuid

internal class ReapplyAccountErasuresImpl(
    private val erasedAccountDataSource: ErasedAccountDataSource,
    private val accountDataSource: AccountDataSource,
    private val accountEraser: AccountEraser,
    private val eventProducer: StandardEventProducer,
    private val transactionManager: TransactionManager,
) : ReapplyAccountErasures {
    override suspend fun invoke(): Result<Unit> = runSuspendCatching {
        val requested = transactionManager.transactionOrThrow { erasedAccountDataSource.getReapplyRequested() }
        requested.map { reapply(it) }.throwIfAnyFailed()
    }

    private suspend fun reapply(userId: Uuid): Result<Unit> = transactionManager.transaction {
        if (!erasedAccountDataSource.claimReapply(userId)) return@transaction
        val account = accountDataSource.getAuthRecordByUserId(userId)
        if (account != null) {
            accountEraser.erase(account.id, userId)
        } else {
            eventProducer.send(AuthEvent.UserDeleted(userId))
        }
    }
}
