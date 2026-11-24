package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.api.entity.AppointmentRequest
import com.bookk.appointments.domain.api.operation.GetAssignedAppointmentRequests
import com.bookk.appointments.domain.datasource.AppointmentPermissionDataSource
import com.bookk.appointments.domain.datasource.AppointmentRequestDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import kotlin.uuid.Uuid

internal class GetAssignedAppointmentRequestsImpl(
    private val requestsDataSource: AppointmentRequestDataSource,
    private val appointmentPermissionDataSource: AppointmentPermissionDataSource,
    private val transactionManager: TransactionManager,
) : GetAssignedAppointmentRequests {
    override suspend fun invoke(
        userId: Uuid,
        businessId: Uuid,
    ): Result<List<AppointmentRequest>> = transactionManager.transaction {
        requireNotSuspended(userId, businessId)
        requestsDataSource.getPendingAssignedTo(userId, businessId)
    }

    private suspend fun requireNotSuspended(userId: Uuid, businessId: Uuid) {
        appointmentPermissionDataSource.getPermission(userId, businessId)
    }
}
