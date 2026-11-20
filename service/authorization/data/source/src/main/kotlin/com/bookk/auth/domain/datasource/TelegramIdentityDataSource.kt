package com.bookk.auth.domain.datasource

import com.bookk.auth.domain.api.authentication.entity.Authentication
import kotlin.uuid.Uuid

interface TelegramIdentityDataSource {
    suspend fun getAccountByTelegramUser(telegramUserId: Long): Authentication?
    suspend fun link(authId: Uuid, telegramUserId: Long)
}
