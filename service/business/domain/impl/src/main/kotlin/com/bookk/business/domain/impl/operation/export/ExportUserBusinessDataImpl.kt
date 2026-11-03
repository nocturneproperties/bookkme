package com.bookk.business.domain.impl.operation.export

import com.bookk.business.domain.api.export.BusinessUserDataExport
import com.bookk.business.domain.api.export.ExportUserBusinessData
import com.bookk.business.domain.datasource.UserDataExportDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import kotlin.uuid.Uuid

internal class ExportUserBusinessDataImpl(
    private val dataSource: UserDataExportDataSource,
    private val transactionManager: TransactionManager,
) : ExportUserBusinessData {

    override suspend fun invoke(userId: Uuid): Result<BusinessUserDataExport> = transactionManager.transaction {
        dataSource.export(userId)
    }
}
