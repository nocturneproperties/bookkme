package com.bookk.auth.data.datasource

import com.bookk.auth.domain.api.telegram.entity.TelegramConfirmationChallenge
import com.bookk.auth.domain.datasource.TelegramConfirmationDataSource
import com.bookk.core.data.DataSource
import com.bookk.core.data.cache.CacheClient
import com.bookk.core.data.cache.get
import com.bookk.core.data.cache.set
import com.bookk.core.data.cache.setIfAbsent
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

internal class TelegramConfirmationDataSourceImpl(
    private val cacheClient: CacheClient<String>,
) : DataSource(), TelegramConfirmationDataSource {

    override suspend fun claimStartCooldown(userId: Uuid): Boolean = mapExceptions {
        cacheClient.setIfAbsent(cooldownKey(userId), CLAIMED, START_COOLDOWN)
    }

    override suspend fun releaseStartCooldown(userId: Uuid): Unit = mapExceptions {
        cacheClient.delete(cooldownKey(userId))
    }

    override suspend fun saveChallenge(
        challengeId: String,
        challenge: TelegramConfirmationChallenge,
    ): Unit = mapExceptions {
        cacheClient.set(challengeKey(challengeId), challenge, CHALLENGE_LIFETIME)
    }

    override suspend fun getChallenge(challengeId: String): TelegramConfirmationChallenge? = mapExceptions {
        cacheClient.get<String, TelegramConfirmationChallenge>(challengeKey(challengeId))
    }

    override suspend fun takeChallenge(challengeId: String): TelegramConfirmationChallenge? = mapExceptions {
        val claimed = cacheClient.setIfAbsent(takenMarkerKey(challengeId), CLAIMED, CHALLENGE_LIFETIME)
        if (!claimed) return@mapExceptions null
        val key = challengeKey(challengeId)
        cacheClient.get<String, TelegramConfirmationChallenge>(key)?.also { cacheClient.delete(key) }
    }

    override suspend fun deleteChallenge(challengeId: String): Unit = mapExceptions {
        cacheClient.delete(challengeKey(challengeId))
    }

    private fun cooldownKey(userId: Uuid): String {
        return "$KEY_PREFIX:cooldown:$userId"
    }

    private fun challengeKey(challengeId: String): String {
        return "$KEY_PREFIX:challenge:$challengeId"
    }

    private fun takenMarkerKey(challengeId: String): String {
        return "${challengeKey(challengeId)}:taken"
    }

    private companion object {
        const val KEY_PREFIX = "telegram-confirmation"
        const val CLAIMED = "1"
        val START_COOLDOWN = 60.seconds
        val CHALLENGE_LIFETIME = 5.minutes
    }
}
