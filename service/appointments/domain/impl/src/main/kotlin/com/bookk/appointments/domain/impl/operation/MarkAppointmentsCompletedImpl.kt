package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.api.operation.MarkAppointmentsCompleted
import com.bookk.appointments.domain.datasource.AppointmentDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import kotlin.time.Clock

internal class MarkAppointmentsCompletedImpl(
    private val appointmentDataSource: AppointmentDataSource,
    private val transactionManager: TransactionManager
) : MarkAppointmentsCompleted {
    override suspend fun invoke(): Result<Unit> = transactionManager.transaction {
        appointmentDataSource.markCompleted(Clock.System.now())
    }
}
