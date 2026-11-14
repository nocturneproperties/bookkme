package com.bookk.user.data.datasource

import com.bookk.core.data.DataSource
import com.bookk.core.data.cache.CacheClient
import com.bookk.core.domain.entity.Error
import com.bookk.core.domain.entity.runSuspendCatching
import com.bookk.user.data.cache.UserCacheStrategy.deleteUser
import com.bookk.user.data.cache.UserCacheStrategy.getUser
import com.bookk.user.data.cache.UserCacheStrategy.save
import com.bookk.user.data.orm.entity.UserEntity
import com.bookk.user.data.orm.table.UserTable
import com.bookk.user.domain.api.entity.User
import com.bookk.user.domain.api.entity.UserEditModel
import com.bookk.user.domain.datasource.UserDataSource
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class UserDataSourceImpl(
    private val cacheClient: CacheClient<String>,
    private val profileCipher: ProfileCipher,
) : DataSource(), UserDataSource {

    override suspend fun insertNewUser(user: User): User = dbQuery {
        val stored = user.copy(phone = user.phone.normalizedPhone())
        requireNoPlaintextHolder(UserTable.email, stored.email)
        requireNoPlaintextHolder(UserTable.phone, stored.phone)
        val encrypted = checkNotNull(profileCipher.encrypt(stored)) { "The new user ${stored.id} has a shredded key" }
        UserEntity.new(stored.id, encrypted, profileCipher.indexes(stored), Clock.System.now())
        runSuspendCatching { cacheClient.save(stored) }
        stored
    }

    override suspend fun updateUser(id: Uuid, user: UserEditModel, updatedAt: Instant): User? = dbQuery {
        val entity = UserEntity.findByIdForUpdate(id) ?: return@dbQuery null
        val current = profileCipher.decrypt(entity)
        val phoneChanged = user.phone != null
        val edited = current.copy(
            name = user.firstName ?: current.name,
            lastName = user.lastName ?: current.lastName,
            phone = if (phoneChanged) user.phone.normalizedPhone() else current.phone,
        )
        if (phoneChanged) requireNoPlaintextHolder(UserTable.phone, edited.phone, except = id)
        val encrypted = profileCipher.encrypt(edited) ?: return@dbQuery null
        entity.store(encrypted, profileCipher.indexes(edited), emailChanged = false, phoneChanged = phoneChanged)
        entity.updatedAt = updatedAt
        edited
    }?.also {
        cacheClient.deleteUser(id)
    }

    override suspend fun changeEmail(id: Uuid, verifiedEmail: String, updatedAt: Instant): User? = dbQuery {
        val entity = UserEntity.findByIdForUpdate(id) ?: return@dbQuery null
        val changed = profileCipher.decrypt(entity).copy(email = verifiedEmail)
        requireNoPlaintextHolder(UserTable.email, changed.email, except = id)
        val encrypted = profileCipher.encrypt(changed) ?: return@dbQuery null
        entity.store(encrypted, profileCipher.indexes(changed), emailChanged = true, phoneChanged = false)
        entity.updatedAt = updatedAt
        changed
    }?.also {
        cacheClient.deleteUser(id)
    }

    override suspend fun getUserById(id: Uuid): User? = dbQuery {
        val cached: User? = cacheClient.getUser(id)
        if (cached != null) return@dbQuery cached
        UserEntity.findById(id)
            ?.let { profileCipher.decrypt(it) }
            ?.also { user ->
                runSuspendCatching { cacheClient.save(user) }
                    .onFailure { cacheClient.deleteUser(user.id) }
            }
    }

    override suspend fun getUserByEmail(email: String): User? = dbQuery {
        val matchesEmail = (UserTable.emailIndex eq profileCipher.index(email)) or (UserTable.email eq email)
        UserEntity.find { matchesEmail }.singleOrNull()?.let { profileCipher.decrypt(it) }
    }

    override suspend fun deleteUser(id: Uuid) = dbQuery<Unit> {
        UserTable.deleteWhere { UserTable.id eq id }
        runSuspendCatching { cacheClient.deleteUser(id) }
    }

    override suspend fun encryptPlaintextProfiles(limit: Int): Int = dbQuery {
        val plaintext = UserEntity.find { UserTable.emailEncrypted.isNull() }.limit(limit).forUpdate().toList()
        plaintext.forEach { entity ->
            val user = entity.plaintextDomain()
            val encrypted =
                checkNotNull(profileCipher.encrypt(user)) { "The stored user ${user.id} has a shredded key" }
            entity.store(encrypted, profileCipher.indexes(user), emailChanged = true, phoneChanged = true)
        }
        plaintext.size
    }

    private fun requireNoPlaintextHolder(column: Column<String?>, contact: String?, except: Uuid? = null) {
        if (contact == null) return
        val someoneElse = except?.let { UserTable.id neq it } ?: Op.TRUE
        val held = UserTable.select(UserTable.id).where { (column eq contact) and someoneElse }.limit(1).any()
        if (held) throw Error.UniqueConstraintFailed("A plaintext profile holds this contact", IllegalStateException())
    }

    private fun String?.normalizedPhone(): String? {
        return this?.trim()?.ifBlank { null }
    }
}
