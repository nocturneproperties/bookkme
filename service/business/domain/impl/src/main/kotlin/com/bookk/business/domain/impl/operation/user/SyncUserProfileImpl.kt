package com.bookk.business.domain.impl.operation.user

import com.bookk.business.domain.api.user.operation.SyncUserProfile
import com.bookk.business.domain.datasource.ClientDataSource
import com.bookk.business.domain.datasource.EmployeeDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class SyncUserProfileImpl(
    private val transactionManager: TransactionManager,
    private val clientDataSource: ClientDataSource,
    private val employeeDataSource: EmployeeDataSource
) : SyncUserProfile {
    override suspend fun invoke(
        userId: Uuid,
        name: String,
        lastName: String,
        email: String,
        phone: String?,
        updatedAt: Instant
    ): Result<Unit> = transactionManager.transaction {
        clientDataSource.updateIntegratedClients(userId, name, lastName, email, phone, updatedAt)
        employeeDataSource.updateIntegratedEmployees(userId, name, lastName, email, phone, updatedAt)
        Unit
    }
}
