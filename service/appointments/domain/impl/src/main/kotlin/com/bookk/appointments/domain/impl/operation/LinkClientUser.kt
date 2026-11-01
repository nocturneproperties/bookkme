package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.api.operation.DeleteUserAppointmentData
import com.bookk.appointments.domain.datasource.AppointmentDataSource
import com.bookk.appointments.domain.datasource.AppointmentRequestDataSource
import com.bookk.appointments.domain.datasource.DeletedUserDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import kotlin.uuid.Uuid

internal class LinkClientUser(
    private val deletedUserDataSource: DeletedUserDataSource,
    private val appointmentDataSource: AppointmentDataSource,
    private val appointmentRequestDataSource: AppointmentRequestDataSource,
    private val deleteUserAppointmentData: DeleteUserAppointmentData,
    private val transactionManager: TransactionManager
) {

    suspend operator fun invoke(businessId: Uuid, clientId: Uuid, userId: Uuid, mergedClientIds: List<Uuid>): Result<Unit> =
        transactionManager.transaction {
            val accountAlreadyDeleted = deletedUserDataSource.isDeletedForShare(userId)
            mergedClientIds.forEach { mergedClientId ->
                appointmentDataSource.reassignClient(businessId, mergedClientId, clientId)
                appointmentRequestDataSource.reassignClient(businessId, mergedClientId, clientId)
            }
            appointmentDataSource.linkClientUser(businessId, clientId, userId)
            appointmentRequestDataSource.linkClientUser(businessId, clientId, userId)
            if (accountAlreadyDeleted) deleteUserAppointmentData(userId).getOrThrow()
        }
}
