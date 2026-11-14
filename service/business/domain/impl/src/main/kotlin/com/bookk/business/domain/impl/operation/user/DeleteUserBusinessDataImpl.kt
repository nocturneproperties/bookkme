package com.bookk.business.domain.impl.operation.user

import com.bookk.business.domain.api.user.operation.DeleteUserBusinessData
import com.bookk.business.domain.datasource.ClientDataSource
import com.bookk.business.domain.datasource.EmployeeDataSource
import com.bookk.core.domain.datasource.transaction.TransactionManager
import library.encryption.SubjectKeys
import library.export.DataExports
import kotlin.uuid.Uuid

internal class DeleteUserBusinessDataImpl(
    private val transactionManager: TransactionManager,
    private val clientDataSource: ClientDataSource,
    private val employeeDataSource: EmployeeDataSource,
    private val dataExports: DataExports,
    private val subjectKeys: SubjectKeys,
) : DeleteUserBusinessData {
    override suspend fun invoke(userId: Uuid): Result<Unit> = transactionManager.transaction {
        val clients = clientDataSource.getClientsOfUserForUpdate(userId)
        val employeeBusinessIds = employeeDataSource.getBusinessIdsOfUserForUpdate(userId)
        val clientIds = clients.map { it.clientId }
        clientDataSource.deleteClients(clientIds)
        employeeDataSource.deleteEmployeesByUserId(userId)
        val affectedBusinessIds = (clients.map { it.businessId } + employeeBusinessIds).toSet()
        if (affectedBusinessIds.isNotEmpty()) dataExports.discardAll(affectedBusinessIds)
        subjectKeys.shredAll(clientIds + userId)
    }
}
