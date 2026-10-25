package com.bookk.notifications.data.orm.entity

import com.bookk.core.data.DecoratorUuidEntityClass
import com.bookk.notifications.data.orm.table.NotificationTelegramTargetTable
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.UuidEntity
import kotlin.uuid.Uuid

internal class NotificationTelegramTargetEntity(id: EntityID<Uuid>) : UuidEntity(id) {
    var userId by NotificationTelegramTargetTable.userId
    var telegramTag by NotificationTelegramTargetTable.telegramTag

    companion object : DecoratorUuidEntityClass<NotificationTelegramTargetEntity>(NotificationTelegramTargetTable)
}
