package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.api.entity.AppointmentPagination
import com.bookk.appointments.domain.api.operation.GetAppointmentHistory
import com.bookk.appointments.domain.datasource.AppointmentDataSource
import com.bookk.appointments.domain.datasource.AppointmentPermissionDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import library.permissions.PermissionAction
import library.permissions.assert
import kotlin.uuid.Uuid

internal class GetAppointmentHistoryImpl(
    private val dataSource: AppointmentDataSource,
    private val appointmentPermissionDataSource: AppointmentPermissionDataSource,
    private val transactionManager: TransactionManager
) : GetAppointmentHistory {
    override suspend fun invoke(
        userId: Uuid,
        businessId: Uuid,
        limit: Int,
        offset: Long,
        query: String?
    ): Result<AppointmentPagination> = transactionManager.transaction {
        appointmentPermissionDataSource.getPermission(userId, businessId).assert(PermissionAction.VIEW)
        dataSource.getAllPaginated(businessId, limit, offset, query)
    }
}