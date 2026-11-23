package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.api.entity.Appointment
import com.bookk.appointments.domain.api.entity.AppointmentStatus
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
import com.bookk.core.data.eventstreaming.StandardEventProducer
import com.bookk.core.data.eventstreaming.send
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.transactionOrThrow
import com.bookk.core.domain.entity.Error
import com.bookk.core.domain.entity.runSuspendCatching
import com.bookk.server.appointments.client.api.event.AppointmentEvent
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
    private val transactionManager: TransactionManager,
    private val eventProducer: StandardEventProducer,
) : UpdateAppointment {

    private val employeeBookings = EmployeeBookings(appointmentDataSource)

    override suspend fun invoke(userId: Uuid, update: AppointmentUpdate): Result<Appointment> = runSuspendCatching {
        if (!update.hasValidServiceSelection()) throw UpdateAppointment.Error.InvalidServiceSelection()
        val existing = transactionManager.transactionOrThrow {
            appointmentDataSource.get(update.id).also { requireReschedulable(userId, it, update) }
        }

        val context = resolveChanges(existing, update)
        val rescheduled = reschedule(existing, update, context)
        val employeeSchedule = context.employee?.schedule

        val stored = transactionManager.transactionOrThrow {
            val settings = settingsDataSource.getForUpdate(existing.businessId) ?: throw Error.NotFound()
            val locked = appointmentDataSource.getForUpdate(update.id)
            if (locked != existing) return@transactionOrThrow null
            requireReschedulable(userId, locked, update)
            val reschedules = update.reschedules(locked)
            val reservesSlot = reschedules || locked.status == AppointmentStatus.SCHEDULED
            if (reservesSlot) {
                settings.requireBookable(rescheduled.date, rescheduled.dateEnd, employeeSchedule)
                if (rescheduled.occupiesOtherTimeThan(locked)) employeeBookings.requireFree(rescheduled, settings)
            }
            if (reschedules) appointmentDataSource.reopen(update.id)
            appointmentDataSource.update(rescheduled).also {
                eventProducer.send(AppointmentEvent.ClientVisitBooked(it.businessId, it.client.id, it.dateEnd))
            }
        }
        stored
            ?: throw Error.TransactionConflict(
                "Appointment ${update.id} changed while it was being rescheduled",
                ConcurrentModificationException(),
            )
    }

    private suspend fun requireReschedulable(userId: Uuid, appointment: Appointment, update: AppointmentUpdate) {
        appointmentPermissionDataSource.getPermission(userId, appointment.businessId)
            .assertOrSelf(PermissionAction.UPDATE, actorId = userId, assigneeId = appointment.employee.userId)
        appointment.requireEditable()
        if (update.reschedules(appointment) && update.date < Clock.System.now()) {
            throw UpdateAppointment.Error.DateInThePastNotAllowed()
        }
    }

    private fun Appointment.occupiesOtherTimeThan(stored: Appointment): Boolean {
        val movesEmployee = employee.id != stored.employee.id
        return movesEmployee || date != stored.date || dateEnd != stored.dateEnd
    }

    private fun AppointmentUpdate.hasValidServiceSelection(): Boolean {
        val serviceIds = services.map { it.serviceId }
        return services.isNotEmpty() && services.all { it.count > 0 } && serviceIds.distinct().size == serviceIds.size
    }

    private fun reschedule(
        existing: Appointment,
        update: AppointmentUpdate,
        context: AppointmentRescheduleContext,
    ): Appointment {
        val storedServices = existing.services.associateBy { it.id }
        val availableServices = storedServices + context.services.associate { it.id to it.toSnapshot() }
        val rescheduling = update.reschedules(existing)
        val employee = if (update.employeeId != existing.employee.id) {
            requireNotNull(context.employee).toSnapshot()
        } else {
            existing.employee
        }

        return existing.copy(
            employee = employee,
            services = update.services.flatMap { requested ->
                List(requested.count) { availableServices.getValue(requested.serviceId) }
            },
            date = update.date,
            note = update.note,
            status = if (rescheduling) AppointmentStatus.SCHEDULED else existing.status,
            completedBy = if (rescheduling) null else existing.completedBy,
            priceAdjustment = if (rescheduling) null else existing.priceAdjustment,
        )
    }

    private suspend fun resolveChanges(existing: Appointment, update: AppointmentUpdate): AppointmentRescheduleContext {
        val employeeId = update.employeeId.takeIf { update.movesSlotOf(existing) }
        val storedServiceIds = existing.services.map { it.id }.toSet()
        val addedServiceIds = update.services.map { it.serviceId }.filterNot { it in storedServiceIds }
        if (employeeId == null && addedServiceIds.isEmpty()) {
            return AppointmentRescheduleContext(employee = null, services = emptyList())
        }
        val keepsEmployee = update.employeeId == existing.employee.id
        val context = businessClient.getAppointmentRescheduleContext(
            businessId = existing.businessId,
            employeeId = employeeId,
            serviceIds = addedServiceIds,
            currentAssignee = keepsEmployee,
        )
        return context.getOrThrow()
    }

    private fun AppointmentUpdate.movesSlotOf(appointment: Appointment): Boolean {
        if (reschedules(appointment)) return true
        val requestedCounts = services.associate { it.serviceId to it.count }
        val storedCounts = appointment.services.groupingBy { it.id }.eachCount()
        return appointment.status == AppointmentStatus.SCHEDULED && requestedCounts != storedCounts
    }

    private fun Employee.toSnapshot() = EmployeeSnapshot(id = id, userId = userId, fullName = "$name $lastName".trim())

    private fun Service.toSnapshot() = ServiceSnapshot(
        id = id,
        name = name,
        groupId = group.id,
        price = price,
        duration = duration,
    )
}
