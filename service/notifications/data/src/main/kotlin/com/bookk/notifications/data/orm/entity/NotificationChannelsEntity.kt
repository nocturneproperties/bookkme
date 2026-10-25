package com.bookk.notifications.data.orm.entity

import com.bookk.core.data.DecoratorUuidEntityClass
import com.bookk.notifications.data.orm.table.NotificationChannelsTable
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.UuidEntity
import kotlin.uuid.Uuid

internal class NotificationChannelsEntity(id: EntityID<Uuid>) : UuidEntity(id) {
    var settingsId by NotificationChannelsTable.settingsId
    var channel by NotificationChannelsTable.channel
    var enabled by NotificationChannelsTable.enabled
    var availableToClients by NotificationChannelsTable.availableToClients

    companion object : DecoratorUuidEntityClass<NotificationChannelsEntity>(NotificationChannelsTable)
}
