package com.bookk.server.user.client

import com.bookk.server.user.client.api.CreateUserRequest
import com.bookk.server.user.client.api.UserSnapshot
import com.bookk.user.domain.api.entity.ProfileName
import kotlin.uuid.Uuid

interface UserClient {
    suspend fun getUserById(userId: Uuid): Result<UserSnapshot>
    suspend fun getUserByEmail(email: String): Result<UserSnapshot>
    suspend fun createUser(request: CreateUserRequest): Result<Uuid>
    suspend fun changeEmail(userId: Uuid, verifiedEmail: String): Result<Unit>
    suspend fun changeEmailAndName(userId: Uuid, verifiedEmail: String, name: ProfileName): Result<Unit>
    suspend fun changeTelegramUsername(userId: Uuid, username: String?): Result<Unit>
}
