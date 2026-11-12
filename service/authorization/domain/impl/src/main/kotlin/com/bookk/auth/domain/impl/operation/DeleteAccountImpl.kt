package com.bookk.auth.domain.impl.operation

import com.bookk.auth.domain.api.delete_account.operation.DeleteAccount
import com.bookk.auth.domain.api.delete_account.operation.DeleteAccount.Error.InvalidCredentials
import com.bookk.auth.domain.datasource.AccountDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.transactionOrThrow
import com.bookk.core.domain.entity.runSuspendCatching
import library.actionconfirmation.ActionConfirmationGate
import library.actionconfirmation.ActionConfirmationGrant
import kotlin.uuid.Uuid

internal class DeleteAccountImpl(
    private val actionConfirmationGate: ActionConfirmationGate,
    private val accountDataSource: AccountDataSource,
    private val accountEraser: AccountEraser,
    private val transactionManager: TransactionManager,
) : DeleteAccount {

    override suspend fun invoke(
        userId: Uuid,
        confirmationToken: String,
    ): Result<Unit> = runSuspendCatching {
        actionConfirmationGate.spend(
            confirmationToken,
            ActionConfirmationGrant.accountDeletion(userId),
            DeleteAccount.Error::ActionConfirmationInvalid,
        ) {
            transactionManager.transactionOrThrow {
                val authRecord = accountDataSource.getAuthRecordByUserId(userId) ?: throw InvalidCredentials()
                accountEraser.erase(authRecord.id, authRecord.userId)
            }
        }
    }
}
