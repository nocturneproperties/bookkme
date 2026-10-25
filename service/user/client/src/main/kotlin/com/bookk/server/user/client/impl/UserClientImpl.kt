package com.bookk.server.user.client.impl

import com.bookk.server.user.client.UserClient
import com.bookk.server.user.client.api.CreateUserRequest
import com.bookk.server.user.client.api.UserSnapshot
import com.bookk.user.domain.api.entity.EmailBody
import com.bookk.user.domain.api.entity.User
import com.bookk.user.domain.api.operation.CreateUser
import com.bookk.user.domain.api.operation.DeleteUser
import com.bookk.user.domain.api.operation.GetUserByEmail
import com.bookk.user.domain.api.operation.GetUserById
import kotlin.uuid.Uuid

internal class UserClientImpl(
    private val getUserById: GetUserById,
    private val getUserByEmail: GetUserByEmail,
    private val createUser: CreateUser,
    private val deleteUser: DeleteUser
) : UserClient {
    override suspend fun getUserById(userId: Uuid): Result<UserSnapshot> {
        return getUserById.invoke(userId)
            .map { UserSnapshot.fromUser(it) }
    }

    override suspend fun getUserByEmail(email: String): Result<UserSnapshot> {
        return getUserByEmail.invoke(EmailBody(email))
            .map { UserSnapshot.fromUser(it) }
    }

    override suspend fun createUser(request: CreateUserRequest): Result<Uuid> {
        return createUser.invoke(
            user = User(
                id = Uuid.random(),
                name = request.name,
                email = request.email,
                lastName = request.lastName,
                phone = null
            )
        ).map { it.id }
    }

    override suspend fun deleteUser(userId: Uuid): Result<Unit> {
        return deleteUser.invoke(userId)
    }


}