package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.api.entity.AppointmentRequest
import com.bookk.appointments.domain.api.operation.GetPendingAppointmentRequests
import com.bookk.appointments.domain.datasource.AppointmentPermissionDataSource
import com.bookk.appointments.domain.datasource.AppointmentRequestDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import library.permissions.PermissionAction
import library.permissions.assert
import kotlin.uuid.Uuid

internal class GetPendingAppointmentRequestsImpl(
    private val requestsDataSource: AppointmentRequestDataSource,
    private val appointmentPermissionDataSource: AppointmentPermissionDataSource,
    private val transactionManager: TransactionManager
) : GetPendingAppointmentRequests {
    override suspend fun invoke(userId: Uuid, businessId: Uuid): Result<List<AppointmentRequest>> = transactionManager.transaction {
        appointmentPermissionDataSource.getPermission(userId, businessId).assert(PermissionAction.VIEW)
        requestsDataSource.getPending(businessId)
    }
}
