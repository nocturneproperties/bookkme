package com.bookk.business.domain.impl.operation.user

import com.bookk.business.domain.api.user.operation.DeleteUserBusinessData
import com.bookk.business.domain.datasource.ClientDataSource
import com.bookk.business.domain.datasource.EmployeeDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import library.export.DataExports
import kotlin.uuid.Uuid

internal class DeleteUserBusinessDataImpl(
    private val transactionManager: TransactionManager,
    private val clientDataSource: ClientDataSource,
    private val employeeDataSource: EmployeeDataSource,
    private val dataExports: DataExports,
) : DeleteUserBusinessData {
    override suspend fun invoke(userId: Uuid): Result<Unit> = transactionManager.transaction {
        val clientBusinessIds = clientDataSource.deleteClientsByUserId(userId).map { it.businessId }
        val employeeBusinessIds = employeeDataSource.deleteEmployeesByUserId(userId)
        val affectedBusinessIds = (clientBusinessIds + employeeBusinessIds).toSet()
        if (affectedBusinessIds.isNotEmpty()) dataExports.discardAll(affectedBusinessIds)
    }
}
