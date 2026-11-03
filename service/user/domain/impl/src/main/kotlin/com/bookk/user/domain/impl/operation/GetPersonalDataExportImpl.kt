package com.bookk.user.domain.impl.operation

import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.user.domain.api.operation.GetPersonalDataExport
import library.export.DataExport
import library.export.DataExports
import kotlin.uuid.Uuid

internal class GetPersonalDataExportImpl(
    private val dataExports: DataExports,
    private val transactionManager: TransactionManager,
) : GetPersonalDataExport {

    override suspend fun invoke(userId: Uuid): Result<DataExport> = transactionManager.transaction {
        dataExports.status(userId)
    }
}
