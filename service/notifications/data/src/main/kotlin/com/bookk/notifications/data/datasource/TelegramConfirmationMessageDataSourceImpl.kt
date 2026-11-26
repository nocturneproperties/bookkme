package com.bookk.notifications.data.datasource

import com.bookk.core.data.DataSource
import com.bookk.core.data.cache.CacheClient
import com.bookk.core.data.cache.deleteIfEquals
import com.bookk.core.data.cache.get
import com.bookk.core.data.cache.set
import com.bookk.notifications.domain.api.entity.PendingTelegramConfirmation
import com.bookk.notifications.domain.datasource.TelegramConfirmationMessageDataSource
import kotlin.time.Duration.Companion.hours
import kotlin.uuid.Uuid

internal class TelegramConfirmationMessageDataSourceImpl(
    private val cacheClient: CacheClient<String>,
) : DataSource(), TelegramConfirmationMessageDataSource {

    override suspend fun save(userId: Uuid, pending: PendingTelegramConfirmation): Unit = mapExceptions {
        cacheClient.set(key(userId), pending, DELETABLE_FOR)
    }

    override suspend fun get(userId: Uuid): PendingTelegramConfirmation? = mapExceptions {
        cacheClient.get<String, PendingTelegramConfirmation>(key(userId))
    }

    override suspend fun forget(userId: Uuid, pending: PendingTelegramConfirmation): Unit = mapExceptions {
        cacheClient.deleteIfEquals(key(userId), pending)
    }

    private fun key(userId: Uuid): String {
        return "telegram-confirmation-message:$userId"
    }

    private companion object {
        val DELETABLE_FOR = 47.hours
    }
}
