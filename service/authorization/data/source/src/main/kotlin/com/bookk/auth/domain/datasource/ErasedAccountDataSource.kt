package com.bookk.auth.domain.datasource

import kotlin.time.Instant
import kotlin.uuid.Uuid

interface ErasedAccountDataSource {
    suspend fun record(userId: Uuid)
    suspend fun getReapplyRequested(): List<Uuid>
    suspend fun claimReapply(userId: Uuid): Boolean
    suspend fun deleteRecordedBefore(cutoff: Instant)
}
