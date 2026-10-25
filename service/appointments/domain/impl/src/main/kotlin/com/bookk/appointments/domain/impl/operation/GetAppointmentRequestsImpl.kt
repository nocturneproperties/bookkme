package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.api.entity.AppointmentRequest
import com.bookk.appointments.domain.api.operation.GetAppointmentRequests
import com.bookk.appointments.domain.datasource.AppointmentRequestDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import kotlin.uuid.Uuid

internal class GetAppointmentRequestsImpl(
    private val requestsDataSource: AppointmentRequestDataSource,
    private val transactionManager: TransactionManager
) : GetAppointmentRequests {
    override suspend fun invoke(userId: Uuid, businessId: Uuid): Result<List<AppointmentRequest>> = transactionManager.transaction {
        requestsDataSource.getPendingByUser(userId, businessId)
    }
}
