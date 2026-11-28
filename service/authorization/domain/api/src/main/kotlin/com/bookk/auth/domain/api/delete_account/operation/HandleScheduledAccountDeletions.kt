package com.bookk.auth.domain.api.delete_account.operation

interface HandleScheduledAccountDeletions {
    suspend operator fun invoke(): Result<Unit>
}
