package com.bookk.auth.domain.impl.operation.identification

import com.bookk.auth.domain.api.identification.operation.DeletePasskey
import com.bookk.auth.domain.datasource.PassKeyDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import kotlin.uuid.Uuid

internal class DeletePasskeyImpl(
    private val passKeyDataSource: PassKeyDataSource,
    private val transactionManager: TransactionManager
) : DeletePasskey {
    override suspend fun invoke(id: Uuid, authId: Uuid): Result<Unit> {
        return transactionManager.transaction {
            val deletedRowCount = passKeyDataSource.deletePasskey(id, authId)
            if (deletedRowCount == 0) {
                throw DeletePasskey.Error.LastPasskey()
            }
        }
    }
}