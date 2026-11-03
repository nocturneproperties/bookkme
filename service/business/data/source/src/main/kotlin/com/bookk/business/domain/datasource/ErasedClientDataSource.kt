package com.bookk.business.domain.datasource

import com.bookk.business.domain.api.client.entity.ErasedClient
import kotlin.time.Instant
import kotlin.uuid.Uuid

interface ErasedClientDataSource {
    suspend fun record(businessId: Uuid, clientId: Uuid)
    suspend fun getReapplyRequested(): List<ErasedClient>
    suspend fun claimReapply(clientId: Uuid): Boolean
    suspend fun deleteRecordedBefore(cutoff: Instant)
}
