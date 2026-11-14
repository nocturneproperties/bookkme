package com.bookk.user.data.datasource

import com.bookk.core.data.DataSource
import com.bookk.core.data.cache.CacheClient
import com.bookk.core.domain.entity.runSuspendCatching
import com.bookk.user.data.cache.UserCacheStrategy.deleteUser
import com.bookk.user.data.cache.UserCacheStrategy.getUser
import com.bookk.user.data.cache.UserCacheStrategy.save
import com.bookk.user.data.orm.entity.UserEntity
import com.bookk.user.data.orm.table.UserTable
import com.bookk.user.domain.api.entity.User
import com.bookk.user.domain.api.entity.UserEditModel
import com.bookk.user.domain.datasource.UserDataSource
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class UserDataSourceImpl(
    private val cacheClient: CacheClient<String>,
    private val profileCipher: ProfileCipher,
) : DataSource(), UserDataSource {

    override suspend fun insertNewUser(user: User): User = dbQuery {
        val stored = user.copy(phone = user.phone.normalizedPhone())
        val encrypted = checkNotNull(profileCipher.encrypt(stored)) { "The new user ${stored.id} has a shredded key" }
        UserEntity.new(stored.id, encrypted, profileCipher.indexes(stored), Clock.System.now())
        runSuspendCatching { cacheClient.save(stored) }
        stored
    }

    override suspend fun updateUser(id: Uuid, user: UserEditModel, updatedAt: Instant): User? = dbQuery {
        val entity = UserEntity.findByIdForUpdate(id) ?: return@dbQuery null
        val current = profileCipher.decrypt(id, entity.profile)
        val phoneChanged = user.phone != null
        val edited = current.copy(
            name = user.firstName ?: current.name,
            lastName = user.lastName ?: current.lastName,
            phone = if (phoneChanged) user.phone.normalizedPhone() else current.phone,
        )
        val encrypted = profileCipher.encrypt(edited) ?: return@dbQuery null
        if (phoneChanged) {
            UserEntity.findByIdAndUpdatePhone(id, encrypted, edited.phone?.let(profileCipher::indexOf), updatedAt)
        } else {
            UserEntity.findByIdAndUpdateProfile(id, encrypted, updatedAt)
        }
        edited
    }?.also {
        cacheClient.deleteUser(id)
    }

    override suspend fun changeEmail(id: Uuid, verifiedEmail: String, updatedAt: Instant): User? = dbQuery {
        val entity = UserEntity.findByIdForUpdate(id) ?: return@dbQuery null
        val changed = profileCipher.decrypt(id, entity.profile).copy(email = verifiedEmail)
        val encrypted = profileCipher.encrypt(changed) ?: return@dbQuery null
        UserEntity.findByIdAndUpdateEmail(id, encrypted, profileCipher.indexOf(verifiedEmail), updatedAt)
        changed
    }?.also {
        cacheClient.deleteUser(id)
    }

    override suspend fun getUserById(id: Uuid): User? = dbQuery {
        val cached: User? = cacheClient.getUser(id)
        if (cached != null) return@dbQuery cached
        UserEntity.findById(id)
            ?.let { profileCipher.decrypt(id, it.profile) }
            ?.also { user ->
                runSuspendCatching { cacheClient.save(user) }
                    .onFailure { cacheClient.deleteUser(user.id) }
            }
    }

    override suspend fun getUserByEmail(email: String): User? = dbQuery {
        UserEntity.find {
            UserTable.emailIndex eq profileCipher.indexOf(email)
        }.singleOrNull()?.let { profileCipher.decrypt(it.id.value, it.profile) }
    }

    override suspend fun deleteUser(id: Uuid) = dbQuery<Unit> {
        UserTable.deleteWhere { UserTable.id eq id }
        runSuspendCatching { cacheClient.deleteUser(id) }
    }

    private fun String?.normalizedPhone(): String? {
        return this?.trim()?.ifBlank { null }
    }
}
