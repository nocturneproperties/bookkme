package com.bookk.notifications.data.orm.table

import com.bookk.core.data.database.BaseUUIDTable

object NotificationSettingsTable : BaseUUIDTable("notification_settings") {
    val userId = uuid("user_id").uniqueIndex()
    val appointmentEnabled = bool("appointment_enabled")
}
