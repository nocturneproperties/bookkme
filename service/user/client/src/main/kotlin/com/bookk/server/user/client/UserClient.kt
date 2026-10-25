package com.bookk.server.user.client

import com.bookk.server.user.client.api.CreateUserRequest
import com.bookk.server.user.client.api.UserSnapshot
import kotlin.uuid.Uuid

interface UserClient {
    suspend fun getUserById(userId: Uuid): Result<UserSnapshot>
    suspend fun getUserByEmail(email: String): Result<UserSnapshot>
    suspend fun createUser(request: CreateUserRequest): Result<Uuid>
    suspend fun deleteUser(userId: Uuid): Result<Unit>
}