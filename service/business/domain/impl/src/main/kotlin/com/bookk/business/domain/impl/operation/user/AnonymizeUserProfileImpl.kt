package com.bookk.business.domain.impl.operation.user

import com.bookk.business.domain.api.user.operation.AnonymizeUserProfile
import com.bookk.business.domain.datasource.BusinessPermissionDataSource
import com.bookk.business.domain.datasource.ClientDataSource
import com.bookk.business.domain.datasource.EmployeeDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import kotlin.uuid.Uuid

internal class AnonymizeUserProfileImpl(
    private val transactionManager: TransactionManager,
    private val clientDataSource: ClientDataSource,
    private val employeeDataSource: EmployeeDataSource,
    private val businessPermissionDataSource: BusinessPermissionDataSource
) : AnonymizeUserProfile {
    override suspend fun invoke(userId: Uuid): Result<Unit> = transactionManager.transaction {
        clientDataSource.anonymizeClientsByUserId(userId)
        employeeDataSource.anonymizeEmployeesByUserId(userId)
        businessPermissionDataSource.deleteUserPermissions(userId)
    }
}
