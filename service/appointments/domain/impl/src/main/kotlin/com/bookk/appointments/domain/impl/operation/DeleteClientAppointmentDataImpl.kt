package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.api.entity.Appointment
import com.bookk.appointments.domain.api.entity.BusinessSnapshot
import com.bookk.appointments.domain.api.operation.DeleteClientAppointmentData
import com.bookk.appointments.domain.datasource.AppointmentDataSource
import com.bookk.appointments.domain.datasource.AppointmentRequestDataSource
import com.bookk.appointments.domain.datasource.AppointmentSubscriptionDataSource
import com.bookk.appointments.domain.datasource.DeletedClientDataSource
import com.bookk.core.data.eventstreaming.StandardEventProducer
import com.bookk.core.data.eventstreaming.send
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.library.serializer.moneyFormatter
import com.bookk.server.appointments.client.api.event.AppointmentEvent
import com.bookk.server.appointments.client.api.event.ClientDeletionCause
import org.slf4j.LoggerFactory
import kotlin.time.Clock
import kotlin.uuid.Uuid

private val deleteClientAppointmentDataLogger = LoggerFactory.getLogger(DeleteClientAppointmentDataImpl::class.java)

internal class DeleteClientAppointmentDataImpl(
    private val deletedClientDataSource: DeletedClientDataSource,
    private val appointmentDataSource: AppointmentDataSource,
    private val requestDataSource: AppointmentRequestDataSource,
    private val subscriptionDataSource: AppointmentSubscriptionDataSource,
    private val eventProducer: StandardEventProducer,
    private val transactionManager: TransactionManager,
) : DeleteClientAppointmentData {
    override suspend fun invoke(businessId: Uuid, clientId: Uuid): Result<Unit> = transactionManager.transaction {
        deletedClientDataSource.markDeleted(businessId, clientId)
        val cancelled = appointmentDataSource.cancelUpcomingForClient(
            businessId,
            clientId,
            Clock.System.now(),
            CLIENT_REMOVED_CANCELLATION_REASON,
        )
        appointmentDataSource.anonymizeClient(businessId, clientId)
        requestDataSource.deleteForClient(businessId, clientId)
        if (cancelled.isNotEmpty()) notifyCancellations(businessId, cancelled)
    }

    private suspend fun notifyCancellations(businessId: Uuid, cancelled: List<Appointment>) {
        val business = subscriptionDataSource.getBusinessSnapshot(businessId) ?: run {
            deleteClientAppointmentDataLogger.error(
                "No business with id $businessId exists, nobody is notified of the cancellations",
            )
            return
        }
        cancelled.forEach { appointment ->
            if (!appointment.isOwnBookingOfEmployee()) eventProducer.send(employeeNotification(appointment, business))
            if (appointment.client.userId != null) eventProducer.send(clientNotification(appointment, business))
        }
    }

    private fun Appointment.isOwnBookingOfEmployee() = client.userId == employee.userId

    private fun employeeNotification(
        appointment: Appointment,
        business: BusinessSnapshot,
    ) = AppointmentEvent.CancelledForDeletedClient(
        employeeUserId = appointment.employee.userId,
        from = appointment.date,
        to = appointment.dateEnd,
        timeZone = business.timeZone,
        businessName = business.name,
        cause = ClientDeletionCause.REMOVED_BY_BUSINESS,
    )

    private fun clientNotification(appointment: Appointment, business: BusinessSnapshot) = AppointmentEvent.Cancelled(
        clientUserId = appointment.client.userId,
        employeeUserId = appointment.employee.userId,
        from = appointment.date,
        to = appointment.dateEnd,
        timeZone = business.timeZone,
        address = business.address,
        businessName = business.name,
        price = moneyFormatter.print(appointment.totalAmount),
        reason = appointment.cancellationReason,
    )

    companion object {
        const val CLIENT_REMOVED_CANCELLATION_REASON = "Client was removed by the business"
    }
}
