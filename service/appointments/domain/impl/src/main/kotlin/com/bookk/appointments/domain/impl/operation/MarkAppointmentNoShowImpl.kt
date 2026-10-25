package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.api.entity.Appointment
import com.bookk.appointments.domain.api.entity.AppointmentStatus
import com.bookk.appointments.domain.api.operation.MarkAppointmentNoShow
import com.bookk.appointments.domain.datasource.AppointmentDataSource
import com.bookk.appointments.domain.datasource.AppointmentPermissionDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import library.permissions.PermissionAction
import library.permissions.assertOrSelf
import kotlin.uuid.Uuid

internal class MarkAppointmentNoShowImpl(
    private val appointmentDataSource: AppointmentDataSource,
    private val appointmentPermissionDataSource: AppointmentPermissionDataSource,
    private val transactionManager: TransactionManager
) : MarkAppointmentNoShow {

    override suspend fun invoke(userId: Uuid, appointmentId: Uuid): Result<Appointment> = transactionManager.transaction {
        val appointment = appointmentDataSource.getForUpdate(appointmentId)
        appointmentPermissionDataSource.getPermission(userId, appointment.businessId)
            .assertOrSelf(PermissionAction.UPDATE, actorId = userId, assigneeId = appointment.employee.userId)
        appointment.requireNoShowMarkable()
        when (appointment.status) {
            AppointmentStatus.NO_SHOW -> appointment
            else -> appointmentDataSource.markNoShow(appointmentId)
        }
    }
}
