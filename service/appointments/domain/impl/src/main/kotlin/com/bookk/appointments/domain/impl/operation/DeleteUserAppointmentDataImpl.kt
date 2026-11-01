package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.api.entity.Appointment
import com.bookk.appointments.domain.api.operation.DeleteUserAppointmentData
import com.bookk.appointments.domain.datasource.AppointmentDataSource
import com.bookk.appointments.domain.datasource.AppointmentPermissionDataSource
import com.bookk.appointments.domain.datasource.AppointmentRequestDataSource
import com.bookk.appointments.domain.datasource.AppointmentSubscriptionDataSource
import com.bookk.appointments.domain.datasource.DeletedUserDataSource
import com.bookk.core.data.eventstreaming.StandardEventProducer
import com.bookk.core.data.eventstreaming.send
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.server.appointments.client.api.event.AppointmentEvent
import org.slf4j.LoggerFactory
import kotlin.time.Clock
import kotlin.uuid.Uuid

private val deleteUserAppointmentDataLogger = LoggerFactory.getLogger(DeleteUserAppointmentDataImpl::class.java)

internal class DeleteUserAppointmentDataImpl(
    private val deletedUserDataSource: DeletedUserDataSource,
    private val appointmentDataSource: AppointmentDataSource,
    private val requestDataSource: AppointmentRequestDataSource,
    private val appointmentPermissionDataSource: AppointmentPermissionDataSource,
    private val subscriptionDataSource: AppointmentSubscriptionDataSource,
    private val eventProducer: StandardEventProducer,
    private val transactionManager: TransactionManager,
) : DeleteUserAppointmentData {
    override suspend fun invoke(userId: Uuid): Result<Unit> = transactionManager.transaction {
        deletedUserDataSource.markDeleted(userId)
        val cancelled = appointmentDataSource.cancelUpcomingForClientUser(
            userId,
            Clock.System.now(),
            ACCOUNT_DELETED_CANCELLATION_REASON
        )
        appointmentDataSource.anonymizeClientUser(userId)
        appointmentDataSource.anonymizeEmployee(userId)
        requestDataSource.deleteForUser(userId)
        appointmentPermissionDataSource.deleteForUser(userId)
        notifyEmployees(cancelled.filter { it.employee.userId != userId })
    }

    private suspend fun notifyEmployees(cancelled: List<Appointment>) {
        cancelled.groupBy { it.businessId }.forEach { (businessId, appointments) ->
            val business = subscriptionDataSource.getBusinessSnapshot(businessId) ?: run {
                deleteUserAppointmentDataLogger.error("No business with id $businessId exists, its employees are not notified of the cancellations")
                return@forEach
            }
            appointments.forEach { appointment ->
                eventProducer.send(
                    AppointmentEvent.CancelledForDeletedClient(
                        employeeUserId = appointment.employee.userId,
                        from = appointment.date,
                        to = appointment.dateEnd,
                        timeZone = business.timeZone,
                        businessName = business.name
                    )
                )
            }
        }
    }

    companion object {
        const val ACCOUNT_DELETED_CANCELLATION_REASON = "User deleted their account"
    }
}
