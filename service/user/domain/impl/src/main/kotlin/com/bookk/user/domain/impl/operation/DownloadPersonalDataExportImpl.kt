package com.bookk.user.domain.impl.operation

import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.entity.Error
import com.bookk.user.domain.api.operation.DownloadPersonalDataExport
import library.export.DataExportDocument
import library.export.DataExports
import kotlin.uuid.Uuid

internal class DownloadPersonalDataExportImpl(
    private val dataExports: DataExports,
    private val transactionManager: TransactionManager,
) : DownloadPersonalDataExport {

    override suspend fun invoke(userId: Uuid): Result<DataExportDocument> = transactionManager.transaction {
        dataExports.document(userId) ?: throw Error.NotFound()
    }
}
