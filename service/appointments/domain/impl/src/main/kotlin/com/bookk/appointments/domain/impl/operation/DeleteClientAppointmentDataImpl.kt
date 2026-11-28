package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.api.entity.Appointment
import com.bookk.appointments.domain.api.entity.BusinessSnapshot
import com.bookk.appointments.domain.api.entity.PendingRequestRef
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
import com.bookk.server.appointments.client.api.event.CancellationReasonKind
import library.encryption.SubjectKeys
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
    private val subjectKeys: SubjectKeys,
    private val transactionManager: TransactionManager,
) : DeleteClientAppointmentData {
    override suspend fun invoke(businessId: Uuid, clientId: Uuid): Result<Unit> = transactionManager.transaction {
        deletedClientDataSource.markDeleted(businessId, clientId)
        val erasure = select(businessId, clientId)
        delete(businessId, clientId, erasure)
        notifyCancellations(erasure)
        eventProducer.sendRequestsResolved(erasure.pendingRequests)
        subjectKeys.shred(clientId)
    }

    private suspend fun select(businessId: Uuid, clientId: Uuid): ClientErasure {
        val upcomingIds = appointmentDataSource.getUpcomingIdsOfClientForUpdate(
            businessId,
            clientId,
            Clock.System.now(),
        )
        val pendingRequests = requestDataSource.getPendingOfClientForUpdate(businessId, clientId)
        val upcoming = appointmentDataSource.getAllByIds(upcomingIds)
        val hasClientToNotify = upcoming.any { it.client.userId != null }
        val business = if (hasClientToNotify) businessToNotify(businessId) else null
        return ClientErasure(upcoming, pendingRequests, business)
    }

    private suspend fun businessToNotify(businessId: Uuid): BusinessSnapshot? {
        return subscriptionDataSource.getBusinessSnapshot(businessId) ?: run {
            deleteClientAppointmentDataLogger.error(
                "No business with id $businessId exists, nobody is notified of the cancellations",
            )
            null
        }
    }

    private suspend fun delete(businessId: Uuid, clientId: Uuid, erasure: ClientErasure) {
        appointmentDataSource.cancelAll(erasure.upcoming.map { it.id }, CLIENT_REMOVED_CANCELLATION_REASON)
        appointmentDataSource.anonymizeClient(businessId, clientId)
        requestDataSource.deleteForClient(businessId, clientId)
    }

    private suspend fun notifyCancellations(erasure: ClientErasure) {
        val business = erasure.business ?: return
        erasure.upcoming
            .filter { it.client.userId != null }
            .forEach { appointment -> eventProducer.send(clientNotification(appointment, business)) }
    }

    private fun clientNotification(appointment: Appointment, business: BusinessSnapshot): AppointmentEvent.Cancelled {
        return AppointmentEvent.Cancelled(
            clientUserId = appointment.client.userId,
            employeeUserId = appointment.employee.userId,
            from = appointment.date,
            to = appointment.dateEnd,
            timeZone = business.timeZone,
            businessName = business.name,
            price = moneyFormatter.print(appointment.totalAmount),
            reason = CLIENT_REMOVED_CANCELLATION_REASON,
            reasonKind = CancellationReasonKind.CLIENT_REMOVED,
        )
    }

    private class ClientErasure(
        val upcoming: List<Appointment>,
        val pendingRequests: List<PendingRequestRef>,
        val business: BusinessSnapshot?,
    )

    companion object {
        const val CLIENT_REMOVED_CANCELLATION_REASON = "Client was removed by the business"
    }
}
