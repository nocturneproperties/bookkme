package com.bookk.notifications.data.orm.entity

import com.bookk.core.data.DecoratorUuidEntityClass
import com.bookk.notifications.data.orm.table.DeviceTable
import com.bookk.notifications.domain.api.entity.Device
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.dao.UuidEntity
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class DeviceEntity(id: EntityID<Uuid>) : UuidEntity(id) {
    var authId by DeviceTable.authId
    var deviceUUID by DeviceTable.deviceUuid
    var userId by DeviceTable.userId
    var notificationToken by DeviceTable.notificationToken
    var notificationTokenUpdatedAt by DeviceTable.notificationTokenUpdatedAt
    var language by DeviceTable.language
    val createdAt by DeviceTable.createdAt
    var updatedAt by DeviceTable.updatedAt

    fun domain() = Device(
        id = id.value,
        authId = authId,
        deviceUuid = deviceUUID,
        userId = userId,
        notificationToken = notificationToken,
        language = language,
    )

    companion object : DecoratorUuidEntityClass<DeviceEntity>(DeviceTable) {
        fun findAndUpdateToken(authId: Uuid, deviceUuid: Uuid, token: String?): DeviceEntity? {
            return find { (DeviceTable.authId eq authId) and (DeviceTable.deviceUuid eq deviceUuid) }
                .forUpdate()
                .firstOrNull()
                ?.also { it.writeToken(token) }
        }

        fun findAndClearTokenRegisteredBefore(authId: Uuid, deviceUuid: Uuid, before: Instant) {
            val registeredBefore = DeviceTable.notificationTokenUpdatedAt.isNull() or
                (DeviceTable.notificationTokenUpdatedAt lessEq before)
            find { (DeviceTable.authId eq authId) and (DeviceTable.deviceUuid eq deviceUuid) and registeredBefore }
                .forUpdate()
                .forEach { it.writeToken(null) }
        }

        private fun DeviceEntity.writeToken(token: String?) {
            notificationToken = token
            notificationTokenUpdatedAt = Clock.System.now()
        }
    }
}
