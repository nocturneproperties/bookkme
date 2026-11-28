package com.bookk.notifications.domain.impl.channel

import com.bookk.notifications.domain.impl.notification.DataPush
import kotlin.uuid.Uuid

internal interface DataPushSender {
    suspend fun sendData(toUserId: Uuid, push: DataPush): Result<Unit>
}
