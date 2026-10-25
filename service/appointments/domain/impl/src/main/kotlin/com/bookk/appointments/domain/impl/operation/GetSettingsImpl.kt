package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.api.entity.AppointmentSettings
import com.bookk.appointments.domain.api.operation.GetSettings
import com.bookk.appointments.domain.datasource.AppointmentPermissionDataSource
import com.bookk.appointments.domain.datasource.AppointmentSettingsDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.entity.Error
import library.permissions.PermissionAction
import library.permissions.assert
import kotlin.uuid.Uuid

internal class GetSettingsImpl(
    private val settingsSource: AppointmentSettingsDataSource,
    private val permissionsSource: AppointmentPermissionDataSource,
    private val transactionManager: TransactionManager
) : GetSettings {
    override suspend fun invoke(
        userId: Uuid,
        businessId: Uuid
    ): Result<AppointmentSettings> = transactionManager.transaction {
        val permission = permissionsSource.getPermission(userId, businessId)
        permission.assert(PermissionAction.VIEW)
        (settingsSource.get(businessId) ?: throw Error.NotFound()).copy(permissions = permission)
    }
}
