package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.api.entity.Appointment
import com.bookk.appointments.domain.api.entity.AppointmentCancellation
import com.bookk.appointments.domain.api.entity.AppointmentStatus
import com.bookk.appointments.domain.api.operation.CancelAppointment
import com.bookk.appointments.domain.datasource.AppointmentDataSource
import com.bookk.appointments.domain.datasource.AppointmentPermissionDataSource
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
import kotlin.uuid.Uuid

private val declineAppointmentLogger = LoggerFactory.getLogger(CancelAppointmentImpl::class.java)

internal class CancelAppointmentImpl(
    private val appointmentDataSource: AppointmentDataSource,
    private val appointmentPermissionDataSource: AppointmentPermissionDataSource,
    private val subscriptionDataSource: AppointmentSubscriptionDataSource,
    private val eventProducer: StandardEventProducer,
    private val transactionManager: TransactionManager
) : CancelAppointment {

    override suspend fun invoke(userId: Uuid, cancellation: AppointmentCancellation): Result<Appointment> = transactionManager.transaction {
        val appointment = appointmentDataSource.getForUpdate(cancellation.id)
        appointmentPermissionDataSource.getPermission(userId, appointment.businessId)
            .assertOrSelf(PermissionAction.UPDATE, actorId = userId, assigneeId = appointment.employee.userId)
        appointment.requireScheduled()
        val cancelled = appointmentDataSource.cancel(cancellation.id, cancellation.reason)
        sendAppointmentCancelledEvent(cancelled)
        appointment.copy(
            status = AppointmentStatus.CANCELLED,
            cancellationReason = cancellation.reason
        )
    }

    private suspend fun sendAppointmentCancelledEvent(appointment: Appointment) {
        val business = subscriptionDataSource.getBusinessSnapshot(appointment.businessId) ?: run {
            declineAppointmentLogger.error("No business with id ${appointment.businessId} exists")
            throw Error.NotFound()
        }
        eventProducer.send(
            AppointmentEvent.Cancelled(
                clientUserId = appointment.client.id,
                clientName = appointment.client.fullName,
                from = appointment.date,
                to = appointment.dateEnd,
                timeZone = business.timeZone,
                businessName = business.name,
                employeeUserId = appointment.employee.userId,
                employeeName = appointment.employee.fullName,
                address = business.address,
                price = moneyFormatter.print(appointment.totalAmount),
                reason = appointment.cancellationReason
            )
        )
    }
}