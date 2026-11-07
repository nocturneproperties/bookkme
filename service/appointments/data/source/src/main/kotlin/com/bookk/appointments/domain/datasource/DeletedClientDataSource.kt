package com.bookk.appointments.domain.datasource

import kotlin.time.Instant
import kotlin.uuid.Uuid

interface DeletedClientDataSource {
    suspend fun markDeleted(businessId: Uuid, clientId: Uuid)
    suspend fun isDeletedForShare(businessId: Uuid, clientId: Uuid): Boolean
    suspend fun deleteMarkedBefore(cutoff: Instant)
}
