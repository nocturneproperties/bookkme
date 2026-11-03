package com.bookk.user.domain.impl.operation

import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.transactionOrThrow
import com.bookk.core.domain.entity.Error
import com.bookk.core.domain.entity.runSuspendCatching
import com.bookk.user.domain.api.operation.StartPersonalDataExport
import com.bookk.user.domain.datasource.UserDataExportDataSource
import library.export.DataExport
import library.export.DataExports
import library.stepup.StepUpGate
import library.stepup.StepUpGrant
import kotlin.uuid.Uuid

internal class StartPersonalDataExportImpl(
    private val stepUpGate: StepUpGate,
    private val dataExports: DataExports,
    private val userDataExportDataSource: UserDataExportDataSource,
    private val transactionManager: TransactionManager,
) : StartPersonalDataExport {

    override suspend fun invoke(userId: Uuid, stepUpToken: String): Result<DataExport> = runSuspendCatching {
        stepUpGate.spend(stepUpToken, StepUpGrant.personalDataExport(userId), StartPersonalDataExport.Error::StepUpInvalid) {
            transactionManager.transactionOrThrow {
                val export = dataExports.start(userId)
                userDataExportDataSource.getProfile(userId) ?: throw Error.NotFound()
                export
            }
        }
    }
}
