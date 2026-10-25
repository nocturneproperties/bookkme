package com.bookk.business.domain.impl.operation.employee

import com.bookk.business.domain.api.employee.operation.DeleteProcessedEmployeeInvitations
import com.bookk.business.domain.datasource.EmployeeInvitationDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

internal class DeleteProcessedEmployeeInvitationsImpl(
    private val invitationDataSource: EmployeeInvitationDataSource,
    private val transactionManager: TransactionManager
) : DeleteProcessedEmployeeInvitations {
    override suspend fun invoke(): Result<Unit> = transactionManager.transaction {
        invitationDataSource.deleteProcessedInvitations(Clock.System.now() - RETENTION_WINDOW)
    }

    private companion object {
        val RETENTION_WINDOW = 30.days
    }
}
