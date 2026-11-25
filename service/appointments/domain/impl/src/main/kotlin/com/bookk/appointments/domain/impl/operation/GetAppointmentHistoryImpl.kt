package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.api.entity.AppointmentHistoryFilter
import com.bookk.appointments.domain.api.entity.AppointmentPage
import com.bookk.appointments.domain.api.entity.HistoryFilterError
import com.bookk.appointments.domain.api.operation.GetAppointmentHistory
import com.bookk.appointments.domain.datasource.AppointmentDataSource
import com.bookk.appointments.domain.datasource.AppointmentPermissionDataSource
import com.bookk.appointments.domain.datasource.AppointmentSettingsDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.entity.PageRequest
import library.permissions.PermissionAction
import library.permissions.assert
import kotlin.uuid.Uuid

internal class GetAppointmentHistoryImpl(
    private val dataSource: AppointmentDataSource,
    private val appointmentPermissionDataSource: AppointmentPermissionDataSource,
    private val settingsDataSource: AppointmentSettingsDataSource,
    private val transactionManager: TransactionManager,
) : GetAppointmentHistory {
    override suspend fun invoke(
        userId: Uuid,
        businessId: Uuid,
        filter: AppointmentHistoryFilter,
        limit: Int?,
        cursor: String?,
    ): Result<AppointmentPage> {
        val page = PageRequest.of(limit, cursor).getOrElse { return Result.failure(it) }
        if (!filter.dates.isValid) return Result.failure(HistoryFilterError.DateRangeInvalid())
        return transactionManager.transaction {
            appointmentPermissionDataSource.getPermission(userId, businessId).assert(PermissionAction.VIEW)
            val starts = settingsDataSource.startBounds(businessId, filter.dates)
            AppointmentPage.of(dataSource.getHistoryPage(businessId, filter, starts, page))
        }
    }
}
