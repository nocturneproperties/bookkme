package com.bookk.business.domain.impl.operation.employee

import com.bookk.business.domain.api.employee.operation.ExpireEmployeeInvitations
import com.bookk.business.domain.datasource.EmployeeInvitationDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

internal class ExpireEmployeeInvitationsImpl(
    private val invitationDataSource: EmployeeInvitationDataSource,
    private val transactionManager: TransactionManager
) : ExpireEmployeeInvitations {
    override suspend fun invoke(): Result<Unit> = transactionManager.transaction {
        invitationDataSource.expireOldInvitations(Clock.System.now() - EXPIRY_WINDOW)
    }

    private companion object {
        val EXPIRY_WINDOW = 7.days
    }
}
