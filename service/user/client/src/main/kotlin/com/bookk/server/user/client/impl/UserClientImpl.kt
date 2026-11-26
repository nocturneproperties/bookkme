package com.bookk.server.user.client.impl

import com.bookk.server.user.client.UserClient
import com.bookk.server.user.client.api.CreateUserRequest
import com.bookk.server.user.client.api.UserSnapshot
import com.bookk.user.domain.api.entity.EmailBody
import com.bookk.user.domain.api.entity.NewUser
import com.bookk.user.domain.api.operation.ChangeTelegramUsername
import com.bookk.user.domain.api.operation.ChangeUserEmail
import com.bookk.user.domain.api.operation.CreateUser
import com.bookk.user.domain.api.operation.GetUserByEmail
import com.bookk.user.domain.api.operation.GetUserById
import kotlin.uuid.Uuid

internal class UserClientImpl(
    private val getUserById: GetUserById,
    private val getUserByEmail: GetUserByEmail,
    private val createUser: CreateUser,
    private val changeUserEmail: ChangeUserEmail,
    private val changeTelegramUsername: ChangeTelegramUsername,
) : UserClient {
    override suspend fun getUserById(userId: Uuid): Result<UserSnapshot> {
        return getUserById.invoke(userId).map { UserSnapshot.fromUser(it) }
    }

    override suspend fun getUserByEmail(email: String): Result<UserSnapshot> {
        return getUserByEmail.invoke(EmailBody(email)).map { UserSnapshot.fromUser(it) }
    }

    override suspend fun createUser(request: CreateUserRequest): Result<Uuid> {
        val newUser = NewUser(
            name = request.name,
            lastName = request.lastName,
            email = request.email,
            acceptedDocuments = request.acceptedDocuments,
            telegramUserId = request.telegramUserId,
            telegramUsername = request.telegramUsername,
        )
        return createUser.invoke(newUser = newUser).map { it.id }
    }

    override suspend fun changeEmail(userId: Uuid, verifiedEmail: String): Result<Unit> {
        return changeUserEmail.invoke(userId, verifiedEmail)
    }

    override suspend fun changeTelegramUsername(userId: Uuid, username: String?): Result<Unit> {
        return changeTelegramUsername.invoke(userId, username)
    }
}
