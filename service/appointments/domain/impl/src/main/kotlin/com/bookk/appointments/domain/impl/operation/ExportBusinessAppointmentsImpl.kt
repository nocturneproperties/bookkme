package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.api.entity.Appointment
import com.bookk.appointments.domain.api.entity.AppointmentRequest
import com.bookk.appointments.domain.api.entity.AppointmentSettings
import com.bookk.appointments.domain.api.entity.ClientSnapshot
import com.bookk.appointments.domain.api.entity.EmployeeSnapshot
import com.bookk.appointments.domain.api.entity.ServiceSnapshot
import com.bookk.appointments.domain.datasource.AppointmentDataSource
import com.bookk.appointments.domain.datasource.AppointmentRequestDataSource
import com.bookk.appointments.domain.datasource.AppointmentSettingsDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.server.appointments.client.api.export.AppointmentsBusinessDataExport
import com.bookk.server.appointments.client.api.export.AppointmentsBusinessDataExport.AppointmentExport
import com.bookk.server.appointments.client.api.export.AppointmentsBusinessDataExport.ClientExport
import com.bookk.server.appointments.client.api.export.AppointmentsBusinessDataExport.EmployeeExport
import com.bookk.server.appointments.client.api.export.AppointmentsBusinessDataExport.RequestExport
import com.bookk.server.appointments.client.api.export.AppointmentsBusinessDataExport.SettingsExport
import com.bookk.server.appointments.client.api.export.AppointmentsUserDataExport.PriceAdjustmentExport
import com.bookk.server.appointments.client.api.export.AppointmentsUserDataExport.ServiceExport
import com.bookk.server.appointments.client.api.export.ExportBusinessAppointments
import kotlin.uuid.Uuid

internal class ExportBusinessAppointmentsImpl(
    private val appointmentDataSource: AppointmentDataSource,
    private val requestDataSource: AppointmentRequestDataSource,
    private val settingsDataSource: AppointmentSettingsDataSource,
    private val transactionManager: TransactionManager,
) : ExportBusinessAppointments {

    override suspend fun invoke(
        businessId: Uuid,
    ): Result<AppointmentsBusinessDataExport> = transactionManager.transaction {
        AppointmentsBusinessDataExport(
            settings = settingsDataSource.get(businessId)?.toExport(),
            appointments = appointmentDataSource.getAll(businessId).sortedBy { it.date }.map { it.toExport() },
            pendingRequests = requestDataSource.getPending(businessId).sortedBy { it.date }.map { it.toExport() },
        )
    }

    private fun AppointmentSettings.toExport() = SettingsExport(
        automaticApproval = automaticApproval,
        automaticCompletion = automaticCompletion,
        breakBetweenAppointmentsMinutes = inBetweenBreakInMinutes,
        appointmentNote = appointmentNote,
    )

    private fun Appointment.toExport() = AppointmentExport(
        id = id,
        start = date,
        end = dateEnd,
        status = status.name,
        services = services.map { it.toExport() },
        employee = employee.toExport(),
        client = client.toExport(),
        note = note.ifBlank { null },
        cancellationReason = cancellationReason.ifBlank { null },
        priceAdjustment = priceAdjustment?.let { adjustment ->
            PriceAdjustmentExport(
                price = adjustment.price.toString(),
                additionalServices = adjustment.additionalServices.map { it.toExport() },
                reason = adjustment.reason?.ifBlank { null },
            )
        },
        completedBy = completedBy?.name,
    )

    private fun AppointmentRequest.toExport() = RequestExport(
        id = id,
        start = date,
        end = dateEnd,
        services = services.map { it.toExport() },
        employee = employee.toExport(),
        client = client.toClientSnapshot().toExport(),
        note = note.ifBlank { null },
    )

    private fun EmployeeSnapshot.toExport() = EmployeeExport(id = id, name = fullName)

    private fun ClientSnapshot.toExport() = ClientExport(
        id = id,
        name = fullName,
        phone = phone,
        email = email,
        hasAccount =
        userId != null,
    )

    private fun ServiceSnapshot.toExport() = ServiceExport(
        name = name,
        price = price.toString(),
        durationMinutes = duration.inWholeMinutes,
    )
}
