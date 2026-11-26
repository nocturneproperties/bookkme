package com.bookk.business.domain.api.user.operation

import kotlin.time.Instant
import kotlin.uuid.Uuid

interface SyncUserProfile {
    suspend operator fun invoke(
        userId: Uuid,
        name: String,
        lastName: String,
        email: String,
        phone: String?,
        telegramUsername: String?,
        updatedAt: Instant,
    ): Result<Unit>
}
