package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.api.operation.DeleteExpiredRequests
import com.bookk.appointments.domain.datasource.AppointmentRequestDataSource
import com.bookk.core.domain.datasource.transaction.TransactionIsolation
import com.bookk.core.domain.datasource.transaction.TransactionManager
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

internal class DeleteExpiredRequestsImpl(
    private val requestDataSource: AppointmentRequestDataSource,
    private val transactionManager: TransactionManager,
) : DeleteExpiredRequests {
    override suspend fun invoke(): Result<Unit> = transactionManager.transaction(TransactionIsolation.READ_COMMITTED) {
        requestDataSource.deleteEndedBefore(Clock.System.now() - RETENTION)
    }

    companion object {
        val RETENTION = 60.days
    }
}
