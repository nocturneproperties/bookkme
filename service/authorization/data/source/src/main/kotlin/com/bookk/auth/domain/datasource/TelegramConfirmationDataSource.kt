package com.bookk.auth.domain.datasource

import com.bookk.auth.domain.api.telegram.entity.TelegramConfirmationChallenge
import kotlin.uuid.Uuid

interface TelegramConfirmationDataSource {
    suspend fun claimStartCooldown(userId: Uuid): Boolean
    suspend fun releaseStartCooldown(userId: Uuid)
    suspend fun saveChallenge(challengeId: String, challenge: TelegramConfirmationChallenge)
    suspend fun getChallenge(challengeId: String): TelegramConfirmationChallenge?
    suspend fun takeChallenge(challengeId: String): TelegramConfirmationChallenge?
    suspend fun deleteChallenge(challengeId: String)
}
