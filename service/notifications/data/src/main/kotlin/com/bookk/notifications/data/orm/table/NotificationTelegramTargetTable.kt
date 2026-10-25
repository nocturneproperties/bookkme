package com.bookk.notifications.data.orm.table

import com.bookk.core.data.database.BaseUUIDTable

object NotificationTelegramTargetTable : BaseUUIDTable("notification_telegram_targets") {
    val userId = uuid("user_id")
    val telegramTag = text("telegram_tag")

    init {
        uniqueIndex(userId)
    }
}
