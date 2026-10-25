package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.api.operation.DeleteUserAppointmentData
import com.bookk.appointments.domain.datasource.AppointmentDataSource
import com.bookk.appointments.domain.datasource.AppointmentPermissionDataSource
import com.bookk.appointments.domain.datasource.AppointmentRequestDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import kotlin.uuid.Uuid

internal class DeleteUserAppointmentDataImpl(
    private val appointmentDataSource: AppointmentDataSource,
    private val requestDataSource: AppointmentRequestDataSource,
    private val appointmentPermissionDataSource: AppointmentPermissionDataSource,
    private val transactionManager: TransactionManager,
) : DeleteUserAppointmentData {
    override suspend fun invoke(userId: Uuid): Result<Unit> = transactionManager.transaction {
        appointmentDataSource.anonymizeForUser(userId)
        requestDataSource.deleteForUser(userId)
        appointmentPermissionDataSource.deleteForUser(userId)
    }
}
