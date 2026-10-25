package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.api.entity.AppointmentSettings
import com.bookk.appointments.domain.api.entity.AppointmentSettingsUpdate
import com.bookk.appointments.domain.api.operation.EditSettings
import com.bookk.appointments.domain.datasource.AppointmentPermissionDataSource
import com.bookk.appointments.domain.datasource.AppointmentSettingsDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import library.permissions.PermissionAction
import library.permissions.assert
import kotlin.uuid.Uuid

internal class EditSettingsImpl(
    private val settingsSource: AppointmentSettingsDataSource,
    private val permissionsSource: AppointmentPermissionDataSource,
    private val transactionManager: TransactionManager
) : EditSettings {
    override suspend fun invoke(
        userId: Uuid,
        update: AppointmentSettingsUpdate
    ): Result<AppointmentSettings> = transactionManager.transaction {
        val permission = permissionsSource.getPermission(userId, update.businessId)
        permission.assert(PermissionAction.UPDATE)
        settingsSource.update(update).copy(permissions = permission)
    }
}
