package com.bookk.auth.domain.datasource

import com.bookk.auth.domain.api.authentication.entity.Authentication
import kotlin.uuid.Uuid

interface TelegramIdentityDataSource {
    suspend fun getAccountByTelegramUser(telegramUserId: Long): Authentication?
    suspend fun hasIdentity(userId: Uuid): Boolean
    suspend fun link(authId: Uuid, telegramUserId: Long, username: String?)
    suspend fun isUsernameSynced(authId: Uuid, username: String?): Boolean
    suspend fun markUsernameSynced(authId: Uuid, username: String?)
}
