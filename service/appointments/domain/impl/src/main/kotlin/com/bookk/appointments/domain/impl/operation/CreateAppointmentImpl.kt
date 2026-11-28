package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.api.entity.Appointment
import com.bookk.appointments.domain.api.entity.AppointmentRepresentation
import com.bookk.appointments.domain.api.entity.AppointmentRequest
import com.bookk.appointments.domain.api.entity.AppointmentSettings
import com.bookk.appointments.domain.api.operation.CreateAppointment
import com.bookk.appointments.domain.datasource.AppointmentDataSource
import com.bookk.appointments.domain.datasource.AppointmentPermissionDataSource
import com.bookk.appointments.domain.datasource.AppointmentRequestDataSource
import com.bookk.appointments.domain.datasource.AppointmentSettingsDataSource
import com.bookk.appointments.domain.datasource.AppointmentSubscriptionDataSource
import com.bookk.core.data.eventstreaming.StandardEventProducer
import com.bookk.core.data.eventstreaming.send
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.transactionOrThrow
import com.bookk.core.domain.entity.Error
import com.bookk.core.domain.entity.runSuspendCatching
import com.bookk.library.serializer.moneyFormatter
import com.bookk.server.appointments.client.api.event.AppointmentEvent
import com.bookk.server.business.client.api.BusinessClient
import library.permissions.PermissionAction
import library.permissions.assertOrSelf
import library.schedule.Schedule
import org.slf4j.LoggerFactory
import kotlin.time.Clock
import kotlin.uuid.Uuid

private val createAppointmentLogger = LoggerFactory.getLogger(CreateAppointmentImpl::class.java)

internal class CreateAppointmentImpl(
    private val appointmentDataSource: AppointmentDataSource,
    private val requestDataSource: AppointmentRequestDataSource,
    private val settingsDataSource: AppointmentSettingsDataSource,
    private val appointmentPermissionDataSource: AppointmentPermissionDataSource,
    private val subscriptionDataSource: AppointmentSubscriptionDataSource,
    private val businessClient: BusinessClient,
    private val transactionManager: TransactionManager,
    private val eventProducer: StandardEventProducer,
) : CreateAppointment {

    private val employeeBookings = EmployeeBookings(appointmentDataSource)

    override suspend fun invoke(
        userId: Uuid,
        appointmentRequestId: Uuid,
    ): Result<Appointment> = runSuspendCatching {
        val pending = transactionManager.transactionOrThrow {
            val request = requestDataSource.get(appointmentRequestId) ?: throw Error.NotFound()
            request.also { requireApprovable(userId, it) }
        }
        val employeeSchedule = currentScheduleOf(pending)
        transactionManager.transactionOrThrow {
            val request = requestDataSource.getForUpdate(appointmentRequestId) ?: throw Error.NotFound()
            val settings = lockSettings(request.businessId)
            requireApprovable(userId, request)
            createAppointment(request, settings, employeeSchedule).also {
                eventProducer.send(AppointmentEvent.RequestResolved(request.id, request.employee.userId))
            }
        }
    }

    override suspend fun createBypassingPermissions(
        request: AppointmentRequest,
        employeeSchedule: Schedule,
    ): Result<Appointment> = transactionManager.transaction {
        createAppointment(request, lockSettings(request.businessId), employeeSchedule)
    }

    private suspend fun requireApprovable(userId: Uuid, request: AppointmentRequest) {
        appointmentPermissionDataSource.getPermission(userId, request.businessId)
            .assertOrSelf(PermissionAction.UPDATE, actorId = userId, assigneeId = request.employee.userId)
        request.requirePending()
    }

    private suspend fun currentScheduleOf(request: AppointmentRequest): Schedule {
        val context = businessClient
            .getAppointmentRescheduleContext(
                request.businessId,
                request.employee.id,
                emptyList(),
                currentAssignee = false,
            )
            .getOrThrow()
        return requireNotNull(context.employee).schedule
    }

    private suspend fun lockSettings(businessId: Uuid): AppointmentSettings {
        return settingsDataSource.getForUpdate(businessId) ?: throw Error.NotFound()
    }

    private suspend fun createAppointment(
        request: AppointmentRequest,
        settings: AppointmentSettings,
        employeeSchedule: Schedule,
    ): Appointment {
        verifyAppointment(request, settings, employeeSchedule)
        return appointmentDataSource.create(request).also {
            requestDataSource.approve(request)
            eventProducer.send(
                AppointmentEvent.ClientVisitBooked(request.businessId, request.client.id, request.dateEnd),
            )
            sendRequestApprovedNotification(request)
        }
    }

    private suspend fun verifyAppointment(
        appointment: AppointmentRepresentation,
        settings: AppointmentSettings,
        employeeSchedule: Schedule,
    ) {
        if (appointment.date < Clock.System.now()) throw CreateAppointment.Error.DateInThePastNotAllowed()
        settings.requireBookable(appointment.date, appointment.dateEnd, employeeSchedule)
        employeeBookings.requireFree(appointment, settings)
    }

    private suspend fun sendRequestApprovedNotification(request: AppointmentRequest) {
        val business = subscriptionDataSource.getBusinessSnapshot(request.businessId) ?: run {
            createAppointmentLogger.error("No business with id ${request.businessId} exists")
            throw Error.NotFound()
        }
        eventProducer.send(
            AppointmentEvent.RequestApproved(
                clientUserId = request.client.userId,
                employeeUserId = request.employee.userId,
                from = request.date,
                to = request.dateEnd,
                timeZone = business.timeZone,
                businessName = business.name,
                address = business.address,
                price = moneyFormatter.print(request.totalAmount),
            ),
        )
    }
}
