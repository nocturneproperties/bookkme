package com.bookk.core.data.repository

import com.bookk.core.data.cache.CacheClient
import com.bookk.core.data.cache.get
import com.bookk.core.data.cache.set
import com.bookk.core.data.cache.setIfAbsent
import kotlinx.serialization.Serializable
import library.idempotency.IdempotencyKey
import library.idempotency.IdempotencyResponse
import library.idempotency.IdempotentResponseRepository
import java.time.OffsetDateTime
import kotlin.time.Duration.Companion.minutes

@Serializable
private class SerializableIdempotencyResponse(
    val isInProgress: Boolean,
    val response: ByteArray
)

class CacheIdempotentResponseRepository(
    private val cacheClient: CacheClient<String>
) : IdempotentResponseRepository {

    override suspend fun storeResponse(
        resource: String,
        idempotencyKey: IdempotencyKey,
        response: ByteArray
    ) {
        val key = "$resource:$idempotencyKey"
        val value = SerializableIdempotencyResponse(isInProgress = false, response = response)
        cacheClient.set(key, value, 10.minutes)
    }

    override suspend fun getResponseOrLock(
        resource: String,
        idempotencyKey: IdempotencyKey
    ): IdempotencyResponse? {
        val key = "$resource:$idempotencyKey"
        repeat(LOCK_ATTEMPTS) {
            if (cacheClient.setIfAbsent(key, inProgressLock(), 10.minutes)) return null
            val response: SerializableIdempotencyResponse = cacheClient.get(key) ?: return@repeat
            return IdempotencyResponse(
                isInProgress = response.isInProgress,
                response = response.response
            )
        }
        return IdempotencyResponse(isInProgress = true, response = ByteArray(0))
    }

    private fun inProgressLock() = SerializableIdempotencyResponse(isInProgress = true, response = ByteArray(0))

    override suspend fun release(resource: String, idempotencyKey: IdempotencyKey) {
        val key = "$resource:$idempotencyKey"
        cacheClient.delete(key)
    }

    override suspend fun deleteExpiredResponses(lastValidDate: OffsetDateTime) {
        //Responses are deleted automatically
    }

    private companion object {
        const val LOCK_ATTEMPTS = 3
    }
}
