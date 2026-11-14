package com.bookk.appointments.domain.impl.operation

import com.bookk.appointments.domain.api.operation.EncryptPlaintextSnapshots
import com.bookk.appointments.domain.datasource.AppointmentDataSource
import com.bookk.appointments.domain.datasource.AppointmentRequestDataSource
import com.bookk.core.domain.datasource.transaction.TransactionIsolation
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.transactionOrThrow
import com.bookk.core.domain.entity.runSuspendCatching

private const val BATCH_SIZE = 100

internal class EncryptPlaintextSnapshotsImpl(
    private val appointmentDataSource: AppointmentDataSource,
    private val appointmentRequestDataSource: AppointmentRequestDataSource,
    private val transactionManager: TransactionManager,
) : EncryptPlaintextSnapshots {

    override suspend fun invoke(): Result<Unit> = runSuspendCatching {
        encryptInBatches { appointmentDataSource.encryptPlaintextAppointments(BATCH_SIZE) }
        encryptInBatches { appointmentRequestDataSource.encryptPlaintextRequests(BATCH_SIZE) }
    }

    private suspend fun encryptInBatches(encryptBatch: suspend () -> Int) {
        do {
            val encrypted = transactionManager.transactionOrThrow(TransactionIsolation.READ_COMMITTED) {
                encryptBatch()
            }
        } while (encrypted == BATCH_SIZE)
    }
}
