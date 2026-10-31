package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.datasource.AppointmentDataSource
import com.bookk.appointments.domain.datasource.AppointmentRequestDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import kotlin.uuid.Uuid

internal class MergeClient(
    private val appointmentDataSource: AppointmentDataSource,
    private val appointmentRequestDataSource: AppointmentRequestDataSource,
    private val transactionManager: TransactionManager
) {

    suspend operator fun invoke(businessId: Uuid, fromClientId: Uuid, toClientId: Uuid) = transactionManager.transaction {
        appointmentDataSource.reassignClient(businessId, fromClientId, toClientId)
        appointmentRequestDataSource.reassignClient(businessId, fromClientId, toClientId)
    }
}
