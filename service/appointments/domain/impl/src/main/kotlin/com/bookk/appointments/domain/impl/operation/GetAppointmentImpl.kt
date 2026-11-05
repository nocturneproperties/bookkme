package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.api.entity.Appointment
import com.bookk.appointments.domain.api.operation.GetAppointment
import com.bookk.appointments.domain.datasource.AppointmentDataSource
import com.bookk.appointments.domain.datasource.AppointmentPermissionDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import library.permissions.PermissionAction
import library.permissions.assert
import kotlin.uuid.Uuid

internal class GetAppointmentImpl(
    private val appointmentDataSource: AppointmentDataSource,
    private val appointmentPermissionDataSource: AppointmentPermissionDataSource,
    private val transactionManager: TransactionManager,
) : GetAppointment {
    override suspend fun invoke(userId: Uuid, appointmentId: Uuid): Result<Appointment> = transactionManager.transaction {
        val appointment = appointmentDataSource.get(appointmentId)
        appointmentPermissionDataSource.getPermission(userId, appointment.businessId).assert(PermissionAction.VIEW)
        appointment
    }
}
