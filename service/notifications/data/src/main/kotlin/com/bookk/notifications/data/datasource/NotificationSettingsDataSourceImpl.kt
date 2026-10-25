package com.bookk.notifications.data.datasource

import com.bookk.core.data.DataSource
import com.bookk.core.domain.entity.Error
import com.bookk.notifications.data.orm.entity.NotificationChannelsEntity
import com.bookk.notifications.data.orm.entity.NotificationSettingsEntity
import com.bookk.notifications.data.orm.table.NotificationChannelsTable
import com.bookk.notifications.data.orm.table.NotificationSettingsTable
import com.bookk.notifications.domain.api.entity.NotificationChannelSettings
import com.bookk.notifications.domain.api.entity.NotificationSettings
import com.bookk.notifications.domain.datasource.NotificationSettingsDataSource
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.upsert
import org.jetbrains.exposed.v1.jdbc.upsertReturning
import kotlin.time.Clock
import kotlin.uuid.Uuid

internal class NotificationSettingsDataSourceImpl : DataSource(), NotificationSettingsDataSource {

    override suspend fun getByUserId(userId: Uuid): NotificationSettings? = dbQuery {
        NotificationSettingsEntity
            .find { NotificationSettingsTable.userId eq userId }
            .firstOrNull()
            ?.domain()
    }

    override suspend fun upsert(settings: NotificationSettings): NotificationSettings = dbQuery {
        val settingId = NotificationSettingsTable.upsertReturning(returning = listOf(NotificationSettingsTable.id)) {
            it[NotificationSettingsTable.id] = settings.id
            it[NotificationSettingsTable.userId] = settings.userId
            it[NotificationSettingsTable.appointmentEnabled] = settings.appointmentEnabled
            it[NotificationSettingsTable.updatedAt] = Clock.System.now()
        }
            .map { it[NotificationSettingsTable.id] }
            .singleOrNull() ?: throw Error.NotFound()

        settings.channels.forEach { channel ->
            NotificationChannelsTable.upsert {
                it[NotificationChannelsTable.id] = channel.id
                it[NotificationChannelsTable.settingsId] = settingId
                it[NotificationChannelsTable.channel] = channel.channel
                it[NotificationChannelsTable.enabled] = channel.enabled
                it[NotificationChannelsTable.availableToClients] = channel.availableToClients
                it[NotificationChannelsTable.updatedAt] = Clock.System.now()
            }
        }

        NotificationSettingsEntity[settingId].domain()
    }

    override suspend fun upsert(channel: NotificationChannelSettings) = dbQuery<Unit> {
        NotificationChannelsEntity.findByIdAndUpdate(channel.id) {
            it.enabled = channel.enabled
            it.availableToClients = channel.availableToClients
        }
    }

    override suspend fun deleteByUserId(userId: Uuid) = dbQuery<Unit> {
        NotificationSettingsTable.deleteWhere { NotificationSettingsTable.userId eq userId }
    }
}
