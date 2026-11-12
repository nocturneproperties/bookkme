package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.api.entity.Appointment
import com.bookk.appointments.domain.api.entity.BusinessSnapshot
import com.bookk.appointments.domain.api.operation.DeleteUserAppointmentData
import com.bookk.appointments.domain.datasource.AppointmentDataSource
import com.bookk.appointments.domain.datasource.AppointmentPermissionDataSource
import com.bookk.appointments.domain.datasource.AppointmentRequestDataSource
import com.bookk.appointments.domain.datasource.AppointmentSubscriptionDataSource
import com.bookk.appointments.domain.datasource.DeletedUserDataSource
import com.bookk.core.data.eventstreaming.StandardEventProducer
import com.bookk.core.data.eventstreaming.send
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.library.serializer.moneyFormatter
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
        val cancelled = appointmentDataSource.cancelUpcomingForUser(
            userId,
            Clock.System.now(),
            asClientReason = ACCOUNT_DELETED_CANCELLATION_REASON,
            asEmployeeReason = EMPLOYEE_DELETED_CANCELLATION_REASON,
        )
        appointmentDataSource.anonymizeClientUser(userId)
        appointmentDataSource.anonymizeEmployee(userId)
        appointmentDataSource.unlinkInitiator(userId)
        requestDataSource.deleteForUser(userId)
        appointmentPermissionDataSource.deleteForUser(userId)
        notifyCancellations(userId, cancelled)
    }

    private suspend fun notifyCancellations(userId: Uuid, cancelled: List<Appointment>) {
        cancelled.filterNot {
            it.isOwnBookingOf(userId)
        }.groupBy { it.businessId }.forEach { (businessId, appointments) ->
            val business = subscriptionDataSource.getBusinessSnapshot(businessId) ?: run {
                deleteUserAppointmentDataLogger.error(
                    "No business with id $businessId exists, nobody is notified of the cancellations",
                )
                return@forEach
            }
            appointments.forEach { appointment ->
                if (appointment.client.userId == userId) {
                    eventProducer.send(employeeNotification(appointment, business))
                } else {
                    releaseAppointmentOfDeletedEmployee(appointment, business)
                }
            }
        }
    }

    private suspend fun releaseAppointmentOfDeletedEmployee(appointment: Appointment, business: BusinessSnapshot) {
        val clientUserId = appointment.client.userId ?: return
        eventProducer.send(clientNotification(clientUserId, appointment, business))
    }

    private fun Appointment.isOwnBookingOf(userId: Uuid) = client.userId == userId && employee.userId == userId

    private fun employeeNotification(
        appointment: Appointment,
        business: BusinessSnapshot,
    ) = AppointmentEvent.CancelledForDeletedClient(
        employeeUserId = appointment.employee.userId,
        from = appointment.date,
        to = appointment.dateEnd,
        timeZone = business.timeZone,
        businessName = business.name,
    )

    private fun clientNotification(
        clientUserId: Uuid,
        appointment: Appointment,
        business: BusinessSnapshot,
    ) = AppointmentEvent.Cancelled(
        clientUserId = clientUserId,
        clientName = appointment.client.fullName,
        employeeUserId = appointment.employee.userId,
        employeeName = DELETED_EMPLOYEE_NAME,
        from = appointment.date,
        to = appointment.dateEnd,
        timeZone = business.timeZone,
        address = business.address,
        businessName = business.name,
        price = moneyFormatter.print(appointment.totalAmount),
        reason = appointment.cancellationReason,
    )

    companion object {
        const val ACCOUNT_DELETED_CANCELLATION_REASON = "User deleted their account"
        const val EMPLOYEE_DELETED_CANCELLATION_REASON = "Employee deleted their account"
        private const val DELETED_EMPLOYEE_NAME = "Deleted User"
    }
}
