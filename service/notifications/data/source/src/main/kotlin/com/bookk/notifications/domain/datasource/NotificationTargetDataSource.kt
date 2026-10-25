package com.bookk.notifications.domain.datasource

import kotlin.time.Instant
import kotlin.uuid.Uuid

interface NotificationTargetDataSource {
    suspend fun getEmail(userId: Uuid): String?
    suspend fun getTelegram(userId: Uuid): String?
    suspend fun insertEmail(userId: Uuid, email: String, updatedAt: Instant)
    suspend fun updateEmail(userId: Uuid, email: String, updatedAt: Instant): Boolean
    suspend fun insertTelegram(userId: Uuid, telegramTag: String)
    suspend fun updateTelegram(userId: Uuid, telegramTag: String): Boolean
    suspend fun deleteByUserId(userId: Uuid)
}
