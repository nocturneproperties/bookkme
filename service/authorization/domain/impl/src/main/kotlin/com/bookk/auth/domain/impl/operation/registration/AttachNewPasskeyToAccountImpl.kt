package com.bookk.auth.domain.impl.operation.registration

import com.bookk.auth.domain.api.identification.entity.AddPasskeyRequest
import com.bookk.auth.domain.api.registration.operation.AttachNewPasskeyToAccount
import com.bookk.auth.domain.api.registration.operation.FinishPasskeyRegistration
import com.bookk.auth.domain.datasource.AccountDataSource
import com.bookk.auth.domain.datasource.PassKeyDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.entity.Error
import kotlin.uuid.Uuid

internal class AttachNewPasskeyToAccountImpl(
    private val finishPasskeyRegistration: FinishPasskeyRegistration,
    private val accountDataSource: AccountDataSource,
    private val passKeyDataSource: PassKeyDataSource,
    private val transactionManager: TransactionManager,
) : AttachNewPasskeyToAccount {
    override suspend fun invoke(
        authId: Uuid,
        request: AddPasskeyRequest,
    ): Result<Unit> = transactionManager.transaction {
        if (!accountDataSource.lockAccount(authId)) throw Error.NotFound()
        if (passKeyDataSource.countPasskeys(authId) >= AttachNewPasskeyToAccount.MAX_PASSKEYS) {
            throw AttachNewPasskeyToAccount.Error.PasskeysLimitReached()
        }
        val passkey = finishPasskeyRegistration.verifyRequest(request).getOrThrow()
        finishPasskeyRegistration.attachOwner(authId, passkey)
    }
}
