package com.bookk.notifications.domain.datasource

import com.bookk.notifications.domain.api.entity.PendingTelegramConfirmation
import kotlin.uuid.Uuid

interface TelegramConfirmationMessageDataSource {
    suspend fun save(userId: Uuid, pending: PendingTelegramConfirmation)
    suspend fun get(userId: Uuid): PendingTelegramConfirmation?
    suspend fun forget(userId: Uuid, pending: PendingTelegramConfirmation)
}
