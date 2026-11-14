package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.api.entity.Appointment
import com.bookk.appointments.domain.api.entity.AppointmentRequest
import com.bookk.appointments.domain.api.entity.ClientSnapshot
import com.bookk.appointments.domain.api.entity.EmployeeSnapshot
import com.bookk.appointments.domain.api.entity.ServiceSnapshot
import com.bookk.appointments.domain.datasource.UserDataExportDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.server.appointments.client.api.export.AppointmentsUserDataExport
import com.bookk.server.appointments.client.api.export.AppointmentsUserDataExport.AppointmentExport
import com.bookk.server.appointments.client.api.export.AppointmentsUserDataExport.AppointmentRequestExport
import com.bookk.server.appointments.client.api.export.AppointmentsUserDataExport.ClientDetailsExport
import com.bookk.server.appointments.client.api.export.AppointmentsUserDataExport.PriceAdjustmentExport
import com.bookk.server.appointments.client.api.export.AppointmentsUserDataExport.Role
import com.bookk.server.appointments.client.api.export.AppointmentsUserDataExport.ServiceExport
import com.bookk.server.appointments.client.api.export.ExportUserAppointments
import kotlin.uuid.Uuid

internal class ExportUserAppointmentsImpl(
    private val dataSource: UserDataExportDataSource,
    private val transactionManager: TransactionManager,
) : ExportUserAppointments {

    override suspend fun invoke(userId: Uuid): Result<AppointmentsUserDataExport> = transactionManager.transaction {
        val appointments = dataSource.getAppointmentsOfUser(userId)
        val requests = dataSource.getRequestsOfUser(userId)
        val businessNames = dataSource.getBusinessNames(
            appointments.map {
                it.businessId
            } + requests.map { it.businessId },
        )
        AppointmentsUserDataExport(
            appointments = appointments.map { it.toExport(userId, businessNames[it.businessId].orEmpty()) },
            requests = requests.map { it.toExport(userId, businessNames[it.businessId].orEmpty()) },
        )
    }

    private fun Appointment.toExport(userId: Uuid, businessName: String): AppointmentExport {
        val roles = rolesOf(userId, client, employee, initiatorUserId)
        val isClient = Role.CLIENT in roles
        return AppointmentExport(
            id = id,
            businessId = businessId,
            businessName = businessName,
            roles = roles,
            start = date,
            end = dateEnd,
            status = status.name,
            services = services.map { it.toExport() },
            employeeName = employee.fullName,
            client = client.detailsIf(isClient, note),
            cancellationReason = cancellationReason.takeIf { isClient }?.ifBlank { null },
            priceAdjustment = priceAdjustment?.let { adjustment ->
                PriceAdjustmentExport(
                    price = adjustment.price.toString(),
                    additionalServices = adjustment.additionalServices.map { it.toExport() },
                    reason = adjustment.reason.takeIf { isClient }?.ifBlank { null },
                )
            },
            completedBy = completedBy?.name,
        )
    }

    private fun AppointmentRequest.toExport(userId: Uuid, businessName: String): AppointmentRequestExport {
        val client = client.toClientSnapshot()
        val roles = rolesOf(userId, client, employee, initiatorUserId)
        val isClient = Role.CLIENT in roles
        return AppointmentRequestExport(
            id = id,
            businessId = businessId,
            businessName = businessName,
            roles = roles,
            start = date,
            end = dateEnd,
            status = status.name,
            services = services.map { it.toExport() },
            employeeName = employee.fullName,
            client = client.detailsIf(isClient, note),
            declineReason = declineReason.takeIf { isClient }?.ifBlank { null },
        )
    }

    private fun rolesOf(
        userId: Uuid,
        client: ClientSnapshot,
        employee: EmployeeSnapshot,
        initiatorUserId: Uuid,
    ): List<Role> = buildList {
        if (client.userId == userId) add(Role.CLIENT)
        if (employee.userId == userId) add(Role.EMPLOYEE)
        if (initiatorUserId == userId) add(Role.BOOKER)
    }

    private fun ClientSnapshot.detailsIf(
        isClient: Boolean,
        note: String,
    ): ClientDetailsExport? = ClientDetailsExport(
        name = fullName,
        phone = phone,
        email = email,
        note = note.ifBlank {
            null
        },
    ).takeIf { isClient }

    private fun ServiceSnapshot.toExport() = ServiceExport(
        name = name,
        price = price.toString(),
        durationMinutes = duration.inWholeMinutes,
    )
}
