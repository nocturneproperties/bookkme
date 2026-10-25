package com.bookk.notifications.data.orm.entity

import com.bookk.core.data.DecoratorUuidEntityClass
import com.bookk.notifications.data.orm.table.DeviceTable
import com.bookk.notifications.domain.api.entity.Device
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.UuidEntity
import kotlin.uuid.Uuid

internal class DeviceEntity(id: EntityID<Uuid>) : UuidEntity(id) {
    var authId by DeviceTable.authId
    var deviceUUID by DeviceTable.deviceUuid
    var userId by DeviceTable.userId
    var notificationToken by DeviceTable.notificationToken
    var language by DeviceTable.language

    fun domain() = Device(
        id = id.value,
        authId = authId,
        deviceUuid = deviceUUID,
        userId = userId,
        notificationToken = notificationToken,
        language = language,
    )

    companion object : DecoratorUuidEntityClass<DeviceEntity>(DeviceTable)
}
