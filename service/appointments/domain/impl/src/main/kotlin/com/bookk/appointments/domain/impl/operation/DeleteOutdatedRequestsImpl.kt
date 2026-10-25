package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.api.operation.DeleteOutdatedRequests
import com.bookk.appointments.domain.datasource.AppointmentRequestDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import kotlin.time.Clock

internal class DeleteOutdatedRequestsImpl(
    private val appointmentRequestDataSource: AppointmentRequestDataSource,
    private val transactionManager: TransactionManager
) : DeleteOutdatedRequests {
    override suspend fun invoke(): Result<Unit> = transactionManager.transaction {
        appointmentRequestDataSource.cancelOutdated(Clock.System.now())
    }
}
