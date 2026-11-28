package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.api.operation.DeleteOutdatedRequests
import com.bookk.appointments.domain.datasource.AppointmentRequestDataSource
import com.bookk.core.data.eventstreaming.StandardEventProducer
import com.bookk.core.domain.datasource.transaction.TransactionIsolation
import com.bookk.core.domain.datasource.transaction.TransactionManager
import kotlin.time.Clock

internal class DeleteOutdatedRequestsImpl(
    private val appointmentRequestDataSource: AppointmentRequestDataSource,
    private val eventProducer: StandardEventProducer,
    private val transactionManager: TransactionManager,
) : DeleteOutdatedRequests {
    override suspend fun invoke(): Result<Unit> = transactionManager.transaction(TransactionIsolation.READ_COMMITTED) {
        val cancelled = appointmentRequestDataSource.cancelOutdated(Clock.System.now())
        eventProducer.sendRequestsResolved(cancelled)
    }
}
