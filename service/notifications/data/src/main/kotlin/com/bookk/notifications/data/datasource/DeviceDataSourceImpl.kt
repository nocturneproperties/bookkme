package com.bookk.notifications.data.datasource

import com.bookk.core.data.DataSource
import com.bookk.core.domain.entity.Error
import com.bookk.core.domain.entity.Language
import com.bookk.notifications.data.orm.entity.DeviceEntity
import com.bookk.notifications.data.orm.table.DeviceTable
import com.bookk.notifications.domain.api.entity.Device
import com.bookk.notifications.domain.datasource.DeviceDataSource
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import kotlin.uuid.Uuid

internal class DeviceDataSourceImpl : DataSource(), DeviceDataSource {

    override suspend fun create(authId: Uuid, deviceUUID: Uuid, userId: Uuid, language: Language): Device = dbQuery {
        DeviceEntity.new {
            this.authId = authId
            this.deviceUUID = deviceUUID
            this.userId = userId
            this.notificationToken = null
            this.language = language
        }.domain()
    }

    override suspend fun getById(id: Uuid): Device? = dbQuery {
        DeviceEntity.findById(id)?.domain()
    }

    override suspend fun getByDeviceUuid(deviceUuid: Uuid): Device? = dbQuery {
        DeviceEntity
            .find { DeviceTable.deviceUuid eq deviceUuid }
            .firstOrNull()
            ?.domain()
    }

    override suspend fun getByAuthId(authId: Uuid): Device? = dbQuery {
        DeviceEntity
            .find { DeviceTable.authId eq authId }
            .firstOrNull()
            ?.domain()
    }

    override suspend fun getByUserId(userId: Uuid): List<Device> = dbQuery {
        DeviceEntity
            .find { DeviceTable.userId eq userId }
            .map { it.domain() }
    }

    override suspend fun updateToken(deviceUuid: Uuid, token: String?): Device = dbQuery {
        DeviceEntity
            .find { DeviceTable.deviceUuid eq deviceUuid }
            .firstOrNull()
            ?.also { it.notificationToken = token }
            ?.domain()
            ?: throw Error.NotFound()
    }

    override suspend fun updateLanguage(deviceUuid: Uuid, language: Language): Device = dbQuery {
        DeviceEntity
            .find { DeviceTable.deviceUuid eq deviceUuid }
            .firstOrNull()
            ?.also { it.language = language }
            ?.domain()
            ?: throw Error.NotFound()
    }

    override suspend fun deleteByDeviceUuid(deviceUuid: Uuid) = dbQuery<Unit> {
        DeviceTable.deleteWhere { DeviceTable.deviceUuid eq deviceUuid }
    }
}
