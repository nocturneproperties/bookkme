package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.api.entity.Appointment
import com.bookk.appointments.domain.api.operation.GetAppointmentsForDate
import com.bookk.appointments.domain.datasource.AppointmentDataSource
import com.bookk.appointments.domain.datasource.AppointmentPermissionDataSource
import com.bookk.appointments.domain.datasource.AppointmentSettingsDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.entity.Error
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atStartOfDayIn
import library.permissions.PermissionAction
import library.permissions.assert
import kotlin.time.Duration.Companion.days
import kotlin.uuid.Uuid

internal class GetAppointmentsForDateImpl(
    private val appointmentDataSource: AppointmentDataSource,
    private val appointmentPermissionDataSource: AppointmentPermissionDataSource,
    private val settingsDataSource: AppointmentSettingsDataSource,
    private val transactionManager: TransactionManager
) : GetAppointmentsForDate {
    override suspend fun invoke(userId: Uuid, businessId: Uuid, date: LocalDate, employeeId: Uuid?): Result<List<Appointment>> = transactionManager.transaction {
        appointmentPermissionDataSource.getPermission(userId, businessId).assert(PermissionAction.VIEW)
        val settings = settingsDataSource.get(businessId) ?: throw Error.NotFound()
        val instant = date.atStartOfDayIn(settings.timeZone)
        val range = instant..(instant + 1.days)
        appointmentDataSource.getAllForDate(businessId, range, employeeId)
    }
}