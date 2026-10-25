package com.bookk.notifications.domain.impl.channel

import com.bookk.notifications.domain.impl.notification.NotificationParameters
import kotlin.uuid.Uuid

internal interface NotificationSender {
    suspend fun send(toUserId: Uuid, params: NotificationParameters): Result<Unit>
}