package com.bookk.notifications.data.orm.table

import com.bookk.core.data.database.BaseUUIDTable
import com.bookk.notifications.domain.api.entity.CommunicationChannel
import org.jetbrains.exposed.v1.core.ReferenceOption

object NotificationChannelsTable : BaseUUIDTable("notification_channels") {
    val settingsId = reference("settings_id", NotificationSettingsTable, ReferenceOption.CASCADE)
    val channel = enumeration("channel", CommunicationChannel::class)
    val enabled = bool("enabled")
    val availableToClients = bool("available_to_clients")

    init {
        uniqueIndex(settingsId, channel)
    }
}
