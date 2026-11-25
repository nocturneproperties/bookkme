package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.api.entity.AppointmentRequestHistoryFilter
import com.bookk.appointments.domain.api.entity.AppointmentRequestPage
import com.bookk.appointments.domain.api.entity.HistoryFilterError
import com.bookk.appointments.domain.api.operation.GetAppointmentRequestHistory
import com.bookk.appointments.domain.datasource.AppointmentPermissionDataSource
import com.bookk.appointments.domain.datasource.AppointmentRequestDataSource
import com.bookk.appointments.domain.datasource.AppointmentSettingsDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.entity.PageRequest
import library.permissions.PermissionAction
import library.permissions.assert
import kotlin.uuid.Uuid

internal class GetAppointmentRequestHistoryImpl(
    private val requestDataSource: AppointmentRequestDataSource,
    private val appointmentPermissionDataSource: AppointmentPermissionDataSource,
    private val settingsDataSource: AppointmentSettingsDataSource,
    private val transactionManager: TransactionManager,
) : GetAppointmentRequestHistory {
    override suspend fun invoke(
        userId: Uuid,
        businessId: Uuid,
        filter: AppointmentRequestHistoryFilter,
        limit: Int?,
        cursor: String?,
    ): Result<AppointmentRequestPage> {
        val page = PageRequest.of(limit, cursor).getOrElse { return Result.failure(it) }
        if (!filter.dates.isValid) return Result.failure(HistoryFilterError.DateRangeInvalid())
        return transactionManager.transaction {
            appointmentPermissionDataSource.getPermission(userId, businessId).assert(PermissionAction.VIEW)
            val starts = settingsDataSource.startBounds(businessId, filter.dates)
            AppointmentRequestPage.of(requestDataSource.getHistoryPage(businessId, filter, starts, page))
        }
    }
}
