package com.bookk.notifications.data.orm.table

import com.bookk.core.data.database.BaseUUIDTable
import com.bookk.core.domain.entity.Language

object DeviceTable : BaseUUIDTable("device") {
    val authId = uuid("auth_id")
    val deviceUuid = uuid("device_uuid").uniqueIndex()
    val userId = uuid("user_id")
    val notificationToken = text("notification_token").nullable()
    val language = enumeration("language", Language::class)

    init {
        index(isUnique = true, authId, deviceUuid, userId)
    }
}
