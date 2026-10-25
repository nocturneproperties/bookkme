package com.bookk.notifications.data.orm.table

import com.bookk.core.data.database.BaseUUIDTable
import org.jetbrains.exposed.v1.datetime.timestamp

object NotificationEmailTargetTable : BaseUUIDTable("notification_email_targets") {
    val userId = uuid("user_id")
    val email = text("email")
    val sourceUpdatedAt = timestamp("source_updated_at").nullable()

    init {
        uniqueIndex(userId)
    }
}
