package com.bookk.business.domain.impl.operation.encryption

import com.bookk.business.domain.api.encryption.operation.EncryptPlaintextContacts
import com.bookk.business.domain.datasource.ClientDataSource
import com.bookk.business.domain.datasource.EmployeeDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.transactionOrThrow
import com.bookk.core.domain.entity.runSuspendCatching

private const val BATCH_SIZE = 100

internal class EncryptPlaintextContactsImpl(
    private val clientDataSource: ClientDataSource,
    private val employeeDataSource: EmployeeDataSource,
    private val transactionManager: TransactionManager,
) : EncryptPlaintextContacts {

    override suspend fun invoke(): Result<Unit> = runSuspendCatching {
        encryptInBatches { clientDataSource.encryptPlaintextClients(BATCH_SIZE) }
        encryptInBatches { employeeDataSource.encryptPlaintextEmployees(BATCH_SIZE) }
    }

    private suspend fun encryptInBatches(encryptBatch: suspend () -> Int) {
        do {
            val encrypted = transactionManager.transactionOrThrow { encryptBatch() }
        } while (encrypted == BATCH_SIZE)
    }
}
