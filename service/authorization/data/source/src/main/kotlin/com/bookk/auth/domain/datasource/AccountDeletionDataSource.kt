package com.bookk.auth.domain.datasource

import com.bookk.auth.domain.api.delete_account.entity.DeletionReminder
import com.bookk.auth.domain.api.delete_account.entity.PendingAccountDeletion
import kotlin.time.Instant
import kotlin.uuid.Uuid

interface AccountDeletionDataSource {
    suspend fun schedule(authId: Uuid, requestedAt: Instant, deletesAt: Instant)
    suspend fun get(authId: Uuid): PendingAccountDeletion?
    suspend fun getByUserId(userId: Uuid): PendingAccountDeletion?
    suspend fun getDueBy(cutoff: Instant): List<PendingAccountDeletion>
    suspend fun setReminder(authId: Uuid, reminder: DeletionReminder)
    suspend fun cancel(authId: Uuid): Boolean
}
