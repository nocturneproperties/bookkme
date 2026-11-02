package com.bookk.auth.data.datasource

import com.bookk.auth.data.map.toDomain
import com.bookk.auth.data.orm.entity.AuthenticationEntity
import com.bookk.auth.data.orm.table.AuthenticationTable
import com.bookk.auth.domain.api.authentication.entity.Authentication
import com.bookk.auth.domain.api.authentication.entity.DormancyStage
import com.bookk.auth.domain.api.authentication.entity.DormantAccount
import com.bookk.auth.domain.datasource.AccountDataSource
import com.bookk.core.data.DataSource
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Clock
import kotlin.time.Instant
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

    override suspend fun recordActivity(authId: Uuid, at: Instant, unlessRecordedSince: Instant) = dbQuery<Unit> {
        AuthenticationTable.update(
            where = {
                (AuthenticationTable.id eq authId) and
                    ((AuthenticationTable.lastActiveAt less unlessRecordedSince) or (AuthenticationTable.dormancyStage neq DormancyStage.ACTIVE))
            },
        ) {
            it[lastActiveAt] = at
            it[dormancyStage] = DormancyStage.ACTIVE
            it[dormancyWarnedAt] = null
        }
    }

    override suspend fun getDormantAccounts(inactiveSince: Instant): List<DormantAccount> = dbQuery {
        AuthenticationTable.selectAll()
            .where { AuthenticationTable.lastActiveAt less inactiveSince }
            .map { it.toDormantAccount() }
    }

    override suspend fun getDormantAccountForUpdate(userId: Uuid): DormantAccount? = dbQuery {
        AuthenticationTable.selectAll()
            .where { AuthenticationTable.userId eq userId }
            .forUpdate()
            .singleOrNull()
            ?.toDormantAccount()
    }

    override suspend fun setDormancyStage(userId: Uuid, stage: DormancyStage, warnedAt: Instant?) = dbQuery<Unit> {
        AuthenticationTable.update(where = { AuthenticationTable.userId eq userId }) {
            it[dormancyStage] = stage
            it[dormancyWarnedAt] = warnedAt
        }
    }

    override suspend fun getLastActiveAt(userIds: Collection<Uuid>): Map<Uuid, Instant> = dbQuery {
        if (userIds.isEmpty()) return@dbQuery emptyMap()
        AuthenticationTable.select(AuthenticationTable.userId, AuthenticationTable.lastActiveAt)
            .where { AuthenticationTable.userId inList userIds }
            .associate { it[AuthenticationTable.userId] to it[AuthenticationTable.lastActiveAt] }
    }

    private fun ResultRow.toDormantAccount() = DormantAccount(
        authId = this[AuthenticationTable.id].value,
        userId = this[AuthenticationTable.userId],
        lastActiveAt = this[AuthenticationTable.lastActiveAt],
        stage = this[AuthenticationTable.dormancyStage],
        warnedAt = this[AuthenticationTable.dormancyWarnedAt],
    )
}
