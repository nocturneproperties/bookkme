package com.bookk.notifications.data.datasource

import com.bookk.core.data.DataSource
import com.bookk.core.domain.entity.Error
import com.bookk.core.domain.entity.Language
import com.bookk.notifications.data.orm.entity.DeviceEntity
import com.bookk.notifications.data.orm.table.DeviceTable
import com.bookk.notifications.domain.api.entity.Device
import com.bookk.notifications.domain.datasource.DeviceDataSource
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.upsert
import kotlin.uuid.Uuid

internal class DeviceDataSourceImpl : DataSource(), DeviceDataSource {

    override suspend fun getOrCreate(authId: Uuid, deviceUUID: Uuid, userId: Uuid, language: Language): Device = dbQuery {
        DeviceTable.upsert(onUpdate = { it[DeviceTable.id] = DeviceTable.id }) {
            it[DeviceTable.authId] = authId
            it[deviceUuid] = deviceUUID
            it[DeviceTable.userId] = userId
            it[notificationToken] = null
            it[DeviceTable.language] = language
        }
        val device = DeviceEntity.find { (DeviceTable.authId eq authId) and (DeviceTable.deviceUuid eq deviceUUID) }
            .forUpdate()
            .single()
            .domain()
        if (device.userId != userId) throw Error.OperationNotAllowed()
        device
    }

    override suspend fun getByUserId(userId: Uuid): List<Device> = dbQuery {
        DeviceEntity
            .find { DeviceTable.userId eq userId }
            .map { it.domain() }
    }

    override suspend fun updateToken(authId: Uuid, deviceUuid: Uuid, token: String?): Device = dbQuery {
        DeviceEntity
            .find { (DeviceTable.authId eq authId) and (DeviceTable.deviceUuid eq deviceUuid) }
            .firstOrNull()
            ?.also { it.notificationToken = token }
            ?.domain()
            ?: throw Error.NotFound()
    }

    override suspend fun updateLanguage(authId: Uuid, deviceUuid: Uuid, language: Language): Device = dbQuery {
        DeviceEntity
            .find { (DeviceTable.authId eq authId) and (DeviceTable.deviceUuid eq deviceUuid) }
            .firstOrNull()
            ?.also { it.language = language }
            ?.domain()
            ?: throw Error.NotFound()
    }

    override suspend fun delete(authId: Uuid, deviceUuid: Uuid) = dbQuery<Unit> {
        DeviceTable.deleteWhere { (DeviceTable.authId eq authId) and (DeviceTable.deviceUuid eq deviceUuid) }
    }
}
