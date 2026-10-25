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
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class UserDataSourceImpl(
    private val cacheClient: CacheClient<String>
) : DataSource(), UserDataSource {

    override suspend fun insertNewUser(user: User) = dbQuery {
        UserTable.insertAndGetId {
            it[name] = user.name
            it[lastName] = user.lastName
            it[email] = user.email
            it[phone] = user.phone
            it[updatedAt] = Clock.System.now()
        }.let {
            user.copy(id = it.value)
        }.also {
            runSuspendCatching { cacheClient.save(it) }
        }
    }

    override suspend fun updateUser(id: Uuid, user: UserEditModel, updatedAt: Instant): User? = dbQuery {
        UserEntity.applyEdit(id, user, updatedAt)?.domain()
    }?.also {
        cacheClient.deleteUser(id)
    }

    override suspend fun getUserById(id: Uuid): User? = dbQuery {
        val cached: User? = cacheClient.getUser(id)
        if (cached != null) return@dbQuery cached
        UserTable.selectAll()
            .where { UserTable.id eq id }
            .map { UserEntity.wrapRow(it).domain() }
            .singleOrNull()
            ?.also { user ->
                runSuspendCatching { cacheClient.save(user) }
                    .onFailure { cacheClient.deleteUser(user.id) }
            }
    }

    override suspend fun getUserByEmail(email: String): User? = dbQuery {
        UserTable.selectAll()
            .where { UserTable.email eq email }
            .map { UserEntity.wrapRow(it).domain() }
            .singleOrNull()
    }

    override suspend fun deleteUser(id: Uuid) = dbQuery<Unit> {
        UserTable.deleteWhere { UserTable.id eq id }
        runSuspendCatching { cacheClient.deleteUser(id) }
    }
}