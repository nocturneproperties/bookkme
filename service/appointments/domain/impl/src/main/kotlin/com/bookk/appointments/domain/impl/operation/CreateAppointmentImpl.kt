package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.api.entity.Appointment
import com.bookk.appointments.domain.api.entity.AppointmentRepresentation
import com.bookk.appointments.domain.api.entity.AppointmentRequest
import com.bookk.appointments.domain.api.operation.CreateAppointment
import com.bookk.appointments.domain.datasource.AppointmentDataSource
import com.bookk.appointments.domain.datasource.AppointmentPermissionDataSource
import com.bookk.appointments.domain.datasource.AppointmentRequestDataSource
import com.bookk.appointments.domain.datasource.AppointmentSettingsDataSource
import com.bookk.appointments.domain.datasource.AppointmentSubscriptionDataSource
import com.bookk.core.data.eventstreaming.StandardEventProducer
import com.bookk.core.data.eventstreaming.send
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.entity.Error
import com.bookk.library.serializer.moneyFormatter
import com.bookk.server.appointments.client.api.event.AppointmentEvent
import library.permissions.PermissionAction
import library.permissions.assertOrSelf
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
    private val transactionManager: TransactionManager,
    private val eventProducer: StandardEventProducer
) : CreateAppointment {

    override suspend fun invoke(
        userId: Uuid,
        appointmentRequestId: Uuid
    ): Result<Appointment> = transactionManager.transaction {
        val request = requestDataSource.getForUpdate(appointmentRequestId) ?: throw Error.NotFound()
        appointmentPermissionDataSource.getPermission(userId, request.businessId)
            .assertOrSelf(PermissionAction.UPDATE, actorId = userId, assigneeId = request.employee.userId)
        request.requirePending()
        createAppointment(request)
    }

    override suspend fun invoke(userId: Uuid, request: AppointmentRequest): Result<Appointment> {
        return transactionManager.transaction {
            createAppointment(request)
        }
    }

    override suspend fun invoke(userId: Uuid, appointment: Appointment, isInstant: Boolean): Result<Appointment> {
        if (isInstant && userId != appointment.userId) return Result.failure(CreateAppointment.Error.InstantAppointmentOnlySelfAllowed())
        return transactionManager.transaction {
            createAppointment(appointment)
        }
    }

    private suspend fun createAppointment(appointment: Appointment): Appointment {
        verifyAppointment(appointment)
        return appointmentDataSource.create(appointment)
    }

    private suspend fun createAppointment(request: AppointmentRequest): Appointment {
        verifyAppointment(request)
        return appointmentDataSource.create(request).also {
            requestDataSource.approve(request)
            sendRequestApprovedNotification(request)
        }
    }

    private suspend fun verifyAppointment(appointment: AppointmentRepresentation) {
        val settings = settingsDataSource.getForUpdate(appointment.businessId) ?: throw Error.NotFound()
        if (appointment.date < Clock.System.now()) throw CreateAppointment.Error.DateInThePastNotAllowed()
        if (!settings.isInWorkday(appointment.date)) throw CreateAppointment.Error.RequestForThisDateNotAllowed()
        if (!settings.isInWorktime(appointment.date, appointment.dateEnd)) throw CreateAppointment.Error.RequestForThisTimeNotAllowed()
        if (appointmentDataSource.hasOverlapsWith(appointment)) throw CreateAppointment.Error.AppointmentForThisTimeExists()
    }

    private suspend fun sendRequestApprovedNotification(request: AppointmentRequest) {
        val business = subscriptionDataSource.getBusinessSnapshot(request.businessId) ?: run {
            createAppointmentLogger.error("No business with id ${request.businessId} exists")
            throw Error.NotFound()
        }
        eventProducer.send(
            AppointmentEvent.RequestApproved(
                clientUserId = request.client.id,
                clientName = request.client.fullName,
                employeeUserId = request.employee.userId,
                employeeName = request.employee.fullName,
                from = request.date,
                to = request.dateEnd,
                timeZone = business.timeZone,
                businessName = business.name,
                address = business.address,
                price = moneyFormatter.print(request.totalAmount)
            )
        )
    }
}
