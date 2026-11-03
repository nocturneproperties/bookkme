package com.bookk.business.domain.impl.operation.export

import com.bookk.business.domain.api.export.DownloadBusinessDataExport
import com.bookk.business.domain.datasource.BusinessDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.entity.Error
import library.export.DataExportDocument
import library.export.DataExports
import kotlin.uuid.Uuid

internal class DownloadBusinessDataExportImpl(
    private val dataExports: DataExports,
    private val businessDataSource: BusinessDataSource,
    private val transactionManager: TransactionManager,
) : DownloadBusinessDataExport {

    override suspend fun invoke(userId: Uuid, businessId: Uuid): Result<DataExportDocument> = transactionManager.transaction {
        businessDataSource.requireOwner(userId, businessId)
        dataExports.document(businessId) ?: throw Error.NotFound()
    }
}
