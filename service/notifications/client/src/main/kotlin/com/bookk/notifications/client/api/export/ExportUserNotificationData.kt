package com.bookk.notifications.client.api.export

import kotlin.uuid.Uuid

interface ExportUserNotificationData {
    suspend operator fun invoke(userId: Uuid): Result<NotificationsUserDataExport>
}
