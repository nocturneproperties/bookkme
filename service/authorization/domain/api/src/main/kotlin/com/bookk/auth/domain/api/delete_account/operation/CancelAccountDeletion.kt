package com.bookk.auth.domain.api.delete_account.operation

import kotlin.uuid.Uuid

interface CancelAccountDeletion {
    suspend operator fun invoke(userId: Uuid): Result<Unit>
}
