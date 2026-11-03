package com.bookk.business.domain.impl.operation.export

import com.bookk.business.domain.api.export.StartBusinessDataExport
import com.bookk.business.domain.datasource.BusinessDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.transactionOrThrow
import com.bookk.core.domain.entity.runSuspendCatching
import library.export.DataExport
import library.export.DataExports
import library.stepup.StepUpGate
import library.stepup.StepUpGrant
import kotlin.uuid.Uuid

internal class StartBusinessDataExportImpl(
    private val stepUpGate: StepUpGate,
    private val dataExports: DataExports,
    private val businessDataSource: BusinessDataSource,
    private val transactionManager: TransactionManager,
) : StartBusinessDataExport {

    override suspend fun invoke(userId: Uuid, businessId: Uuid, stepUpToken: String): Result<DataExport> = runSuspendCatching {
        transactionManager.transactionOrThrow { businessDataSource.requireOwner(userId, businessId) }
        stepUpGate.spend(stepUpToken, StepUpGrant.businessDataExport(userId, businessId), StartBusinessDataExport.Error::StepUpInvalid) {
            transactionManager.transactionOrThrow {
                dataExports.start(businessId).also { businessDataSource.requireOwner(userId, businessId) }
            }
        }
    }
}
