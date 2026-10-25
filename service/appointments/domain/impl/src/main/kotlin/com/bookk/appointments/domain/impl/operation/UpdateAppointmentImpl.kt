package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.api.entity.Appointment
import com.bookk.appointments.domain.api.entity.AppointmentUpdate
import com.bookk.appointments.domain.api.entity.EmployeeSnapshot
import com.bookk.appointments.domain.api.entity.ServiceSnapshot
import com.bookk.appointments.domain.api.operation.UpdateAppointment
import com.bookk.appointments.domain.datasource.AppointmentDataSource
import com.bookk.appointments.domain.datasource.AppointmentPermissionDataSource
import com.bookk.appointments.domain.datasource.AppointmentSettingsDataSource
import com.bookk.business.domain.api.appointment.entity.AppointmentRescheduleContext
import com.bookk.business.domain.api.employee.entity.Employee
import com.bookk.business.domain.api.service.entity.Service
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.entity.Error
import com.bookk.server.business.client.api.BusinessClient
import library.permissions.PermissionAction
import library.permissions.assertOrSelf
import kotlin.time.Clock
import kotlin.uuid.Uuid

internal class UpdateAppointmentImpl(
    private val appointmentDataSource: AppointmentDataSource,
    private val settingsDataSource: AppointmentSettingsDataSource,
    private val appointmentPermissionDataSource: AppointmentPermissionDataSource,
    private val businessClient: BusinessClient,
    private val transactionManager: TransactionManager
) : UpdateAppointment {
    override suspend fun invoke(userId: Uuid, update: AppointmentUpdate): Result<Appointment> {
        if (!update.hasValidServiceSelection()) return Result.failure(UpdateAppointment.Error.InvalidServiceSelection())
        return transactionManager.transaction {
            val existing = appointmentDataSource.getForUpdate(update.id)
            appointmentPermissionDataSource.getPermission(userId, existing.businessId)
                .assertOrSelf(PermissionAction.UPDATE, actorId = userId, assigneeId = existing.employee.userId)
            existing.requireScheduled()
            if (update.date < Clock.System.now()) throw UpdateAppointment.Error.DateInThePastNotAllowed()

            val rescheduled = reschedule(existing, update)

            val settings = settingsDataSource.getForUpdate(existing.businessId) ?: throw Error.NotFound()
            if (!settings.isInWorkday(rescheduled.date)) throw UpdateAppointment.Error.RequestForThisDateNotAllowed()
            if (!settings.isInWorktime(rescheduled.date, rescheduled.dateEnd)) throw UpdateAppointment.Error.RequestForThisTimeNotAllowed()
            if (appointmentDataSource.hasOverlapsWith(rescheduled)) throw UpdateAppointment.Error.AppointmentForThisTimeExists()
            appointmentDataSource.update(rescheduled)
        }
    }

    private fun AppointmentUpdate.hasValidServiceSelection(): Boolean {
        val serviceIds = services.map { it.serviceId }
        return services.isNotEmpty() && services.all { it.count > 0 } && serviceIds.distinct().size == serviceIds.size
    }

    private suspend fun reschedule(existing: Appointment, update: AppointmentUpdate): Appointment {
        val storedServices = existing.services.associateBy { it.id }
        val changedEmployeeId = update.employeeId.takeIf { it != existing.employee.id }
        val addedServiceIds = update.services.map { it.serviceId }.filterNot { it in storedServices }
        val context = resolveChanges(existing.businessId, changedEmployeeId, addedServiceIds)
        val availableServices = storedServices + context.services.associate { it.id to it.toSnapshot() }

        return existing.copy(
            employee = context.employee?.toSnapshot() ?: existing.employee,
            services = update.services.flatMap { requested -> List(requested.count) { availableServices.getValue(requested.serviceId) } },
            date = update.date,
            note = update.note
        )
    }

    private suspend fun resolveChanges(businessId: Uuid, employeeId: Uuid?, serviceIds: List<Uuid>): AppointmentRescheduleContext {
        if (employeeId == null && serviceIds.isEmpty()) return AppointmentRescheduleContext(employee = null, services = emptyList())
        return businessClient.getAppointmentRescheduleContext(businessId, employeeId, serviceIds).getOrThrow()
    }

    private fun Employee.toSnapshot() = EmployeeSnapshot(id = id, userId = userId, fullName = "$name $lastName".trim())

    private fun Service.toSnapshot() = ServiceSnapshot(id = id, name = name, groupId = group.id, price = price, duration = duration)
}
