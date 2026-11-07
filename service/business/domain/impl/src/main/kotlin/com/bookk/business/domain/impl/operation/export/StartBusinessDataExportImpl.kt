package com.bookk.business.domain.impl.operation.export

import com.bookk.business.domain.api.export.StartBusinessDataExport
import com.bookk.business.domain.datasource.BusinessDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.core.domain.datasource.transaction.transactionOrThrow
import com.bookk.core.domain.entity.runSuspendCatching
import library.actionconfirmation.ActionConfirmationGate
import library.actionconfirmation.ActionConfirmationGrant
import library.export.DataExport
import library.export.DataExports
import kotlin.uuid.Uuid

internal class StartBusinessDataExportImpl(
    private val actionConfirmationGate: ActionConfirmationGate,
    private val dataExports: DataExports,
    private val businessDataSource: BusinessDataSource,
    private val transactionManager: TransactionManager,
) : StartBusinessDataExport {

    override suspend fun invoke(userId: Uuid, businessId: Uuid, confirmationToken: String): Result<DataExport> = runSuspendCatching {
        transactionManager.transactionOrThrow { businessDataSource.requireOwner(userId, businessId) }
        actionConfirmationGate.spend(confirmationToken, ActionConfirmationGrant.businessDataExport(userId, businessId), StartBusinessDataExport.Error::ActionConfirmationInvalid) {
            transactionManager.transactionOrThrow {
                dataExports.start(businessId).also { businessDataSource.requireOwner(userId, businessId) }
            }
        }
    }
}
