package com.bookk.user.domain.impl.operation

import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.transactionOrThrow
import com.bookk.core.domain.entity.Error
import com.bookk.core.domain.entity.runSuspendCatching
import com.bookk.user.domain.api.operation.StartPersonalDataExport
import com.bookk.user.domain.datasource.UserDataExportDataSource
import library.actionconfirmation.ActionConfirmationGate
import library.actionconfirmation.ActionConfirmationGrant
import library.export.DataExport
import library.export.DataExports
import kotlin.uuid.Uuid

internal class StartPersonalDataExportImpl(
    private val actionConfirmationGate: ActionConfirmationGate,
    private val dataExports: DataExports,
    private val userDataExportDataSource: UserDataExportDataSource,
    private val transactionManager: TransactionManager,
) : StartPersonalDataExport {

    override suspend fun invoke(userId: Uuid, confirmationToken: String): Result<DataExport> = runSuspendCatching {
        actionConfirmationGate.spend(confirmationToken, ActionConfirmationGrant.personalDataExport(userId), StartPersonalDataExport.Error::ActionConfirmationInvalid) {
            transactionManager.transactionOrThrow {
                val export = dataExports.start(userId)
                userDataExportDataSource.getProfile(userId) ?: throw Error.NotFound()
                export
            }
        }
    }
}
