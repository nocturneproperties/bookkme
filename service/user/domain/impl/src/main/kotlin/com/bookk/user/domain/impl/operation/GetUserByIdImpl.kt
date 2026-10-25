package com.bookk.user.domain.impl.operation

import com.bookk.core.domain.datasource.transaction.TransactionManager
import com.bookk.user.domain.api.entity.User
import com.bookk.user.domain.api.operation.GetUserById
import com.bookk.user.domain.api.operation.GetUserById.Error
import com.bookk.user.domain.datasource.UserDataSource
import kotlin.uuid.Uuid

internal class GetUserByIdImpl(
    private val userDataSource: UserDataSource,
    private val transactionManager: TransactionManager
) : GetUserById {

    override suspend fun invoke(userId: Uuid): Result<User> = transactionManager.transaction {
        userDataSource.getUserById(userId) ?: throw Error.UserNotFound()
    }

}