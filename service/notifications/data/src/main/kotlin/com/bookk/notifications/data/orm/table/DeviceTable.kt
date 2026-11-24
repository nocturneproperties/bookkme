package com.bookk.notifications.data.orm.table

import com.bookk.core.data.database.BaseUUIDTable
import com.bookk.core.domain.entity.Language
import org.jetbrains.exposed.v1.datetime.timestamp

object DeviceTable : BaseUUIDTable("device") {
    val authId = uuid("auth_id")
    val deviceUuid = uuid("device_uuid")
    val userId = uuid("user_id")
    val notificationToken = text("notification_token").nullable()
    val notificationTokenUpdatedAt = timestamp("notification_token_updated_at").nullable()
    val language = enumeration("language", Language::class)

    init {
        uniqueIndex(authId, deviceUuid)
    }
}
