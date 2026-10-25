package com.bookk.notifications.domain.api

import kotlin.uuid.Uuid

interface DeleteUserNotificationData {
    suspend operator fun invoke(userId: Uuid): Result<Unit>
}
