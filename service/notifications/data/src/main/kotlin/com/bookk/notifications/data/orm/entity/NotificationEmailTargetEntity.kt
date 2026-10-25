package com.bookk.notifications.data.orm.entity

import com.bookk.core.data.DecoratorUuidEntityClass
import com.bookk.notifications.data.orm.table.NotificationEmailTargetTable
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.UuidEntity
import kotlin.uuid.Uuid

internal class NotificationEmailTargetEntity(id: EntityID<Uuid>) : UuidEntity(id) {
    var userId by NotificationEmailTargetTable.userId
    var email by NotificationEmailTargetTable.email

    companion object : DecoratorUuidEntityClass<NotificationEmailTargetEntity>(NotificationEmailTargetTable)
}
