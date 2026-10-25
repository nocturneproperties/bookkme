package com.bookk.business.domain.impl.operation.employee

import com.bookk.business.domain.api.employee.entity.EmployeeInvitation
import com.bookk.business.domain.api.employee.operation.GetEmployeeInvitations
import com.bookk.business.domain.datasource.EmployeeInvitationDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import kotlin.uuid.Uuid

internal class GetEmployeeInvitationsImpl(
    private val invitationDataSource: EmployeeInvitationDataSource,
    private val transactionManager: TransactionManager
) : GetEmployeeInvitations {
    override suspend fun invoke(userId: Uuid, businessId: Uuid): Result<List<EmployeeInvitation>> =
        transactionManager.transaction {
            invitationDataSource.getInvitationsByInviter(businessId, userId)
        }
}
