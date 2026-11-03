package com.bookk.auth.domain.impl.operation

import com.bookk.auth.domain.api.authentication.entity.FinishAssertionRequest
import com.bookk.auth.domain.api.authentication.operation.FinishAssertion
import com.bookk.auth.domain.api.authentication.operation.finishOwnedBy
import com.bookk.auth.domain.api.delete_account.operation.DeleteAccount
import com.bookk.auth.domain.api.delete_account.operation.DeleteAccount.Error.InvalidCredentials
import com.bookk.auth.domain.datasource.AccountDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import kotlin.uuid.Uuid

internal class DeleteAccountImpl(
    private val finishAssertion: FinishAssertion,
    private val accountDataSource: AccountDataSource,
    private val accountEraser: AccountEraser,
    private val transactionManager: TransactionManager,
) : DeleteAccount {

    override suspend fun invoke(
        userId: Uuid,
        request: FinishAssertionRequest,
    ): Result<Unit> = transactionManager.transaction {
        finishAssertion.finishOwnedBy(userId, request).getOrThrow()
        val authRecord = accountDataSource.getAuthRecordByUserId(userId) ?: throw InvalidCredentials()
        accountEraser.erase(authRecord.id, authRecord.userId)
    }
}
