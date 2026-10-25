package com.bookk.auth.data.datasource

import com.bookk.auth.data.map.toDomain
import com.bookk.auth.data.orm.entity.AuthenticationEntity
import com.bookk.auth.data.orm.table.AuthenticationTable
import com.bookk.auth.domain.api.authentication.entity.Authentication
import com.bookk.auth.domain.datasource.AccountDataSource
import com.bookk.core.data.DataSource
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import kotlin.time.Clock
import kotlin.uuid.Uuid

internal class AccountDataSourceImpl : DataSource(), AccountDataSource {

    override suspend fun createAuthorization(info: Authentication): Authentication = dbQuery {
        AuthenticationTable.insertAndGetId {
            it[userId] = info.userId
            it[uuid] = info.uuid
            it[updatedAt] = Clock.System.now()
        }.let {
            info.copy(id = it.value)
        }
    }

    override suspend fun getAuthRecordById(id: Uuid): Authentication? = dbQuery {
        AuthenticationTable.selectAll()
            .where { AuthenticationTable.id eq id }
            .map { AuthenticationEntity.wrapRow(it).toDomain() }
            .singleOrNull()
    }

    override suspend fun getAuthRecordByUUID(uuid: Uuid): Authentication? = dbQuery {
        AuthenticationTable.selectAll()
            .where { AuthenticationTable.uuid eq uuid }
            .map { AuthenticationEntity.wrapRow(it).toDomain() }
            .singleOrNull()
    }

    override suspend fun getAuthRecordByUserId(userId: Uuid): Authentication? = dbQuery {
        AuthenticationTable.selectAll()
            .where { AuthenticationTable.userId eq userId }
            .map { AuthenticationEntity.wrapRow(it).toDomain() }
            .singleOrNull()
    }

    override suspend fun deleteAuthorization(authId: Uuid) = dbQuery<Unit> {
        AuthenticationTable.deleteWhere {
            AuthenticationTable.id eq authId
        }
    }
}