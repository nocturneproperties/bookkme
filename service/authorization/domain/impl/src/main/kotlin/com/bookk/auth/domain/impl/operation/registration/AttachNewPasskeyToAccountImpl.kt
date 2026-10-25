package com.bookk.auth.domain.impl.operation.registration

import com.bookk.auth.domain.api.identification.entity.AddPasskeyRequest
import com.bookk.auth.domain.api.registration.operation.AttachNewPasskeyToAccount
import com.bookk.auth.domain.api.registration.operation.FinishPasskeyRegistration
import com.bookk.core.domain.datasource.transaction.TransactionManager
import kotlin.uuid.Uuid

internal class AttachNewPasskeyToAccountImpl(
    private val finishPasskeyRegistration: FinishPasskeyRegistration,
    private val transactionManager: TransactionManager
) : AttachNewPasskeyToAccount {
    override suspend fun invoke(authId: Uuid, request: AddPasskeyRequest): Result<Unit> {
        return transactionManager.transaction {
            val passkey = finishPasskeyRegistration.verifyRequest(request).getOrThrow()
            finishPasskeyRegistration.attachOwner(authId, passkey)
        }
    }
}