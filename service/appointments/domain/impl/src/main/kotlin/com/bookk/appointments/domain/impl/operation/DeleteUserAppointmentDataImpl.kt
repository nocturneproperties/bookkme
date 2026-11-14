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
import library.encryption.SubjectKeys
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
    private val subjectKeys: SubjectKeys,
    private val transactionManager: TransactionManager,
) : DeleteUserAppointmentData {
    override suspend fun invoke(userId: Uuid): Result<Unit> = transactionManager.transaction {
        deletedUserDataSource.markDeleted(userId)
        val erasure = select(userId)
        delete(userId, erasure)
        notifyCancellations(userId, erasure)
        subjectKeys.shredAll(erasure.subjectIds)
    }

    private suspend fun select(userId: Uuid): UserErasure {
        val upcomingIds = appointmentDataSource.getUpcomingIdsOfUserForUpdate(userId, Clock.System.now())
        val clientIds = appointmentDataSource.getClientIdsOfUserForUpdate(userId) +
            requestDataSource.getClientIdsOfUserForUpdate(userId)
        val ownedBusinessIds = subscriptionDataSource.getBusinessIdsOwnedByForUpdate(userId)
        val ownedBusinessClientIds = subscriptionDataSource.getClientIdsForUpdate(ownedBusinessIds)
        val upcoming = appointmentDataSource.getAllByIds(upcomingIds)
        return UserErasure(
            upcoming = upcoming,
            businesses = businessesToNotify(userId, upcoming),
            ownedBusinessIds = ownedBusinessIds,
            subjectIds = setOf(userId) + clientIds + ownedBusinessClientIds,
        )
    }

    private suspend fun businessesToNotify(userId: Uuid, upcoming: List<Appointment>): Map<Uuid, BusinessSnapshot> {
        val businessIds = upcoming.filterNot { it.isOwnBookingOf(userId) }.map { it.businessId }.distinct()
        return businessIds.mapNotNull { businessId ->
            subscriptionDataSource.getBusinessSnapshot(businessId) ?: run {
                deleteUserAppointmentDataLogger.error(
                    "No business with id $businessId exists, nobody is notified of the cancellations",
                )
                null
            }
        }.associateBy { it.id }
    }

    private suspend fun delete(userId: Uuid, erasure: UserErasure) {
        val (asClient, asEmployee) = erasure.upcoming.partition { it.client.userId == userId }
        appointmentDataSource.cancelAll(asClient.map { it.id }, ACCOUNT_DELETED_CANCELLATION_REASON)
        appointmentDataSource.cancelAll(asEmployee.map { it.id }, EMPLOYEE_DELETED_CANCELLATION_REASON)
        appointmentDataSource.anonymizeClientUser(userId)
        appointmentDataSource.anonymizeEmployee(userId)
        appointmentDataSource.unlinkInitiator(userId)
        requestDataSource.deleteForUser(userId)
        appointmentPermissionDataSource.deleteForUser(userId)
        subscriptionDataSource.detachBusinesses(erasure.ownedBusinessIds)
    }

    private suspend fun notifyCancellations(userId: Uuid, erasure: UserErasure) {
        erasure.upcoming.filterNot { it.isOwnBookingOf(userId) }.forEach { appointment ->
            val business = erasure.businesses[appointment.businessId] ?: return@forEach
            if (appointment.client.userId == userId) {
                eventProducer.send(employeeNotification(appointment, business))
            } else {
                releaseAppointmentOfDeletedEmployee(appointment, business)
            }
        }
    }

    private suspend fun releaseAppointmentOfDeletedEmployee(appointment: Appointment, business: BusinessSnapshot) {
        val clientUserId = appointment.client.userId ?: return
        eventProducer.send(clientNotification(clientUserId, appointment, business))
    }

    private fun Appointment.isOwnBookingOf(userId: Uuid): Boolean {
        return client.userId == userId && employee.userId == userId
    }

    private fun employeeNotification(
        appointment: Appointment,
        business: BusinessSnapshot,
    ): AppointmentEvent.CancelledForDeletedClient {
        return AppointmentEvent.CancelledForDeletedClient(
            employeeUserId = appointment.employee.userId,
            from = appointment.date,
            to = appointment.dateEnd,
            timeZone = business.timeZone,
            businessName = business.name,
        )
    }

    private fun clientNotification(
        clientUserId: Uuid,
        appointment: Appointment,
        business: BusinessSnapshot,
    ): AppointmentEvent.Cancelled {
        return AppointmentEvent.Cancelled(
            clientUserId = clientUserId,
            employeeUserId = appointment.employee.userId,
            from = appointment.date,
            to = appointment.dateEnd,
            timeZone = business.timeZone,
            address = business.address,
            businessName = business.name,
            price = moneyFormatter.print(appointment.totalAmount),
            reason = EMPLOYEE_DELETED_CANCELLATION_REASON,
        )
    }

    private class UserErasure(
        val upcoming: List<Appointment>,
        val businesses: Map<Uuid, BusinessSnapshot>,
        val ownedBusinessIds: List<Uuid>,
        val subjectIds: Set<Uuid>,
    )

    companion object {
        const val ACCOUNT_DELETED_CANCELLATION_REASON = "User deleted their account"
        const val EMPLOYEE_DELETED_CANCELLATION_REASON = "Employee deleted their account"
    }
}
