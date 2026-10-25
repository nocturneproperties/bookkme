package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.api.entity.AppointmentCancellation
import com.bookk.appointments.domain.api.entity.AppointmentRequest
import com.bookk.appointments.domain.api.operation.DeclineAppointmentRequest
import com.bookk.appointments.domain.datasource.AppointmentPermissionDataSource
import com.bookk.appointments.domain.datasource.AppointmentRequestDataSource
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

private val declineAppointmentRequestLogger = LoggerFactory.getLogger(CancelAppointmentImpl::class.java)

internal class DeclineAppointmentRequestImpl(
    private val requestDataSource: AppointmentRequestDataSource,
    private val appointmentPermissionDataSource: AppointmentPermissionDataSource,
    private val subscriptionDataSource: AppointmentSubscriptionDataSource,
    private val eventProducer: StandardEventProducer,
    private val transactionManager: TransactionManager
) : DeclineAppointmentRequest {

    override suspend fun invoke(userId: Uuid, cancellation: AppointmentCancellation): Result<Unit> = transactionManager.transaction {
        val appointment = requestDataSource.getForUpdate(cancellation.id) ?: throw Error.NotFound()
        appointmentPermissionDataSource.getPermission(userId, appointment.businessId)
            .assertOrSelf(PermissionAction.UPDATE, actorId = userId, assigneeId = appointment.employee.userId)
        appointment.requirePending()
        val declined = requestDataSource.decline(cancellation.id, cancellation.reason)
        sendRequestDeclinedEvent(declined)
    }

    private suspend fun sendRequestDeclinedEvent(appointment: AppointmentRequest) {
        val business = subscriptionDataSource.getBusinessSnapshot(appointment.businessId) ?: run {
            declineAppointmentRequestLogger.error("No business with id ${appointment.businessId} exists")
            throw Error.NotFound()
        }
        eventProducer.send(
            AppointmentEvent.RequestRejected(
                clientUserId = appointment.client.id,
                clientName = appointment.client.fullName,
                employeeUserId = appointment.employee.userId,
                employeeName = appointment.employee.fullName,
                from = appointment.date,
                to = appointment.dateEnd,
                timeZone = business.timeZone,
                businessName = business.name,
                address = business.address,
                price = moneyFormatter.print(appointment.totalAmount),
                declineReason = appointment.declineReason
            )
        )
    }
}