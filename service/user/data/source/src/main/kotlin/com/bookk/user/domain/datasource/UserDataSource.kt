package com.bookk.user.domain.datasource

import com.bookk.user.domain.api.entity.ProfileName
import com.bookk.user.domain.api.entity.TelegramUsernameChange
import com.bookk.user.domain.api.entity.User
import com.bookk.user.domain.api.entity.UserEditModel
import kotlin.time.Instant
import kotlin.uuid.Uuid

interface UserDataSource {
    suspend fun insertNewUser(user: User): User
    suspend fun updateUser(id: Uuid, user: UserEditModel, updatedAt: Instant): User?
    suspend fun changeEmail(id: Uuid, verifiedEmail: String, updatedAt: Instant): User?
    suspend fun changeEmailAndName(id: Uuid, verifiedEmail: String, name: ProfileName, updatedAt: Instant): User?
    suspend fun changeTelegramUsername(id: Uuid, username: String?, updatedAt: Instant): TelegramUsernameChange?
    suspend fun getUserById(id: Uuid): User?
    suspend fun getUserByEmail(email: String): User?
    suspend fun deleteUser(id: Uuid)
}
