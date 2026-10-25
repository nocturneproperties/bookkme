package com.bookk.notifications.data.orm.entity

import com.bookk.core.data.DecoratorUuidEntityClass
import com.bookk.notifications.data.orm.table.NotificationChannelsTable
import com.bookk.notifications.data.orm.table.NotificationSettingsTable
import com.bookk.notifications.domain.api.entity.NotificationChannelSettings
import com.bookk.notifications.domain.api.entity.NotificationSettings
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.UuidEntity
import kotlin.uuid.Uuid

internal class NotificationSettingsEntity(id: EntityID<Uuid>) : UuidEntity(id) {
    var userId by NotificationSettingsTable.userId
    var appointmentEnabled by NotificationSettingsTable.appointmentEnabled
    val channels by NotificationChannelsEntity referrersOn NotificationChannelsTable.settingsId

    fun domain() = NotificationSettings(
        id = id.value,
        userId = userId,
        appointmentEnabled = appointmentEnabled,
        channels = channels.map {
            NotificationChannelSettings(
                id = it.id.value,
                channel = it.channel,
                enabled = it.enabled,
                availableToClients = it.availableToClients,
            )
        },
    )

    companion object : DecoratorUuidEntityClass<NotificationSettingsEntity>(NotificationSettingsTable)
}
