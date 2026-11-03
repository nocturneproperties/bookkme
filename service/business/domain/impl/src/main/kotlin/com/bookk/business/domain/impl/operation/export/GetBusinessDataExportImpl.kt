package com.bookk.business.domain.impl.operation.export

import com.bookk.business.domain.api.export.GetBusinessDataExport
import com.bookk.business.domain.datasource.BusinessDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import library.export.DataExport
import library.export.DataExports
import kotlin.uuid.Uuid

internal class GetBusinessDataExportImpl(
    private val dataExports: DataExports,
    private val businessDataSource: BusinessDataSource,
    private val transactionManager: TransactionManager,
) : GetBusinessDataExport {

    override suspend fun invoke(userId: Uuid, businessId: Uuid): Result<DataExport> = transactionManager.transaction {
        businessDataSource.requireOwner(userId, businessId)
        dataExports.status(businessId)
    }
}
