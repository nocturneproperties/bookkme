package com.bookk.user.domain.impl.operation

import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.user.domain.api.operation.DeleteUser
import com.bookk.user.domain.datasource.UserDataSource
import kotlin.uuid.Uuid

internal class DeleteUserImpl(
    private val userDataSource: UserDataSource,
    private val transactionManager: TransactionManager
) : DeleteUser {

    override suspend fun invoke(userId: Uuid): Result<Unit> = transactionManager.transaction {
        userDataSource.deleteUser(userId)
    }

}