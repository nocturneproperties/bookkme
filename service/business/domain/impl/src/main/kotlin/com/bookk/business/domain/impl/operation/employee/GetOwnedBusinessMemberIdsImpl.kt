package com.bookk.business.domain.impl.operation.employee

import com.bookk.business.domain.api.employee.operation.GetOwnedBusinessMemberIds
import com.bookk.business.domain.datasource.EmployeeDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import kotlin.uuid.Uuid

internal class GetOwnedBusinessMemberIdsImpl(
    private val employeeDataSource: EmployeeDataSource,
    private val transactionManager: TransactionManager,
) : GetOwnedBusinessMemberIds {
    override suspend fun invoke(ownerUserId: Uuid): Result<List<Uuid>> = transactionManager.transaction {
        employeeDataSource.getOwnedBusinessMemberUserIds(ownerUserId)
    }
}
