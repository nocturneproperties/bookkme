package com.bookk.auth.domain.api.delete_account.operation

import com.bookk.auth.domain.api.delete_account.entity.AccountDeletionStatus
import kotlin.uuid.Uuid

interface GetAccountDeletion {
    suspend operator fun invoke(userId: Uuid): Result<AccountDeletionStatus>
}
