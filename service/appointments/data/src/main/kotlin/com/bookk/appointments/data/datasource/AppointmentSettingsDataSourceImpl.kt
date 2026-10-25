package com.bookk.appointments.data.datasource

import com.bookk.appointments.data.orm.entity.SettingsEntity
import com.bookk.appointments.data.orm.table.SettingsTable
import com.bookk.appointments.domain.api.entity.AppointmentSettings
import com.bookk.appointments.domain.api.entity.AppointmentSettingsUpdate
import com.bookk.appointments.domain.datasource.AppointmentSettingsDataSource
import com.bookk.core.data.DataSource
import com.bookk.core.domain.entity.Error
import org.jetbrains.exposed.v1.core.eq
import kotlin.uuid.Uuid

internal class AppointmentSettingsDataSourceImpl : DataSource(), AppointmentSettingsDataSource {
    override suspend fun create(settings: AppointmentSettings): AppointmentSettings = dbQuery {
        SettingsEntity.new(settings).domain()
    }

    override suspend fun update(update: AppointmentSettingsUpdate): AppointmentSettings = dbQuery {
        SettingsEntity.findByBusinessIdAndUpdate(update)?.domain() ?: throw Error.NotFound()
    }

    override suspend fun get(businessId: Uuid): AppointmentSettings? = dbQuery {
        SettingsEntity.find {
            SettingsTable.businessId eq businessId
        }
            .singleOrNull()
            ?.domain()
    }

    override suspend fun getForUpdate(businessId: Uuid): AppointmentSettings? = dbQuery {
        SettingsEntity.find {
            SettingsTable.businessId eq businessId
        }
            .forUpdate()
            .singleOrNull()
            ?.domain()
    }
}
