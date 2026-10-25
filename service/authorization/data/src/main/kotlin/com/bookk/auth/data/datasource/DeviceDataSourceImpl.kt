package com.bookk.auth.data.datasource

import com.bookk.auth.data.map.toDomain
import com.bookk.auth.data.orm.entity.AuthDeviceEntity
import com.bookk.auth.data.orm.table.AuthDeviceTable
import com.bookk.auth.data.orm.table.AuthenticationTable
import com.bookk.auth.domain.api.identification.entity.Device
import com.bookk.auth.domain.datasource.DeviceDataSource
import com.bookk.core.data.DataSource
import com.bookk.core.domain.entity.Error
import com.bookk.core.domain.entity.Language
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.innerJoin
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.deleteReturning
import org.jetbrains.exposed.v1.jdbc.insertIgnoreAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class DeviceDataSourceImpl : DataSource(), DeviceDataSource {
    override suspend fun attachRefreshTokenToDevice(deviceId: Uuid, tokenId: Uuid, tokenHash: String) = dbQuery<Unit> {
        val now = Clock.System.now()
        AuthDeviceTable.update(where = { AuthDeviceTable.id eq deviceId }) {
            it[isSignedIn] = true
            it[refreshTokenId] = tokenId
            it[refreshTokenHash] = tokenHash
            it[refreshTokenExpiresAt] = now.plus(REFRESH_TOKEN_TTL)
            it[previousRefreshTokenId] = null
            it[previousRefreshTokenHash] = null
            it[lastLogInAt] = now
            it[updatedAt] = now
        }
    }

    override suspend fun rotateRefreshToken(deviceId: Uuid, tokenId: Uuid, tokenHash: String) = dbQuery<Unit> {
        val (id, hash) = AuthDeviceTable
            .selectAll()
            .where { AuthDeviceTable.id eq deviceId }
            .map { it[AuthDeviceTable.refreshTokenId] to it[AuthDeviceTable.refreshTokenHash] }
            .singleOrNull() ?: throw Error.NotFound()

        AuthDeviceTable.update(where = { AuthDeviceTable.id eq deviceId }) {
            it[isSignedIn] = true
            it[previousRefreshTokenId] = id
            it[previousRefreshTokenHash] = hash
            it[refreshTokenId] = tokenId
            it[refreshTokenHash] = tokenHash
            it[refreshTokenExpiresAt] = Clock.System.now().plus(REFRESH_TOKEN_TTL)
            it[updatedAt] = Clock.System.now()
        }
    }

    override suspend fun getDeviceById(deviceId: Uuid): Device? = dbQuery {
        AuthDeviceTable
            .innerJoin(AuthenticationTable, onColumn = { userAuthId }, otherColumn = { id })
            .selectAll()
            .where { AuthDeviceTable.id eq deviceId }
            .map { AuthDeviceEntity.wrapRow(it).toDomain() }
            .singleOrNull()
    }

    override suspend fun getDeviceByRefreshTokenId(tokenId: Uuid): Device? = dbQuery {
        AuthDeviceTable
            .innerJoin(AuthenticationTable, onColumn = { userAuthId }, otherColumn = { id })
            .selectAll()
            .where {
                AuthDeviceTable.refreshTokenId.eq(tokenId)
                    .and(AuthDeviceTable.refreshTokenExpiresAt.greater(Clock.System.now()))
                    .or(AuthDeviceTable.previousRefreshTokenId.eq(tokenId))
            }
            .map { AuthDeviceEntity.wrapRow(it).toDomain() }
            .singleOrNull()
    }

    override suspend fun getDeviceByAuthIdAndUUID(authId: Uuid, deviceUUID: Uuid): Device? = dbQuery {
        AuthDeviceTable
            .innerJoin(
                otherTable = AuthenticationTable,
                onColumn = { userAuthId },
                otherColumn = { id },
                additionalConstraint = { AuthenticationTable.id eq authId }
            )
            .selectAll()
            .where { AuthDeviceTable.deviceUUID eq deviceUUID }
            .map { AuthDeviceEntity.wrapRow(it).toDomain() }
            .singleOrNull()
    }

    override suspend fun getDevices(authId: Uuid): List<Device> = dbQuery {
        AuthDeviceTable
            .innerJoin(
                otherTable = AuthenticationTable,
                onColumn = { userAuthId },
                otherColumn = { id },
                additionalConstraint = { AuthDeviceTable.userAuthId eq authId }
            )
            .selectAll()
            .map { AuthDeviceEntity.wrapRow(it).toDomain() }
            .toList()
    }

    override suspend fun deleteTokenFromDevice(deviceId: Uuid) = dbQuery<Unit> {
        AuthDeviceTable.update(
            where = { AuthDeviceTable.id eq deviceId }
        ) {
            it[isSignedIn] = false
            it[refreshTokenId] = null
            it[refreshTokenHash] = null
            it[previousRefreshTokenId] = null
            it[previousRefreshTokenHash] = null
            it[updatedAt] = Clock.System.now()
        }
    }

    override suspend fun insertDevice(authId: Uuid, uuid: Uuid, name: String, language: Language) = dbQuery {
        AuthDeviceTable.insertIgnoreAndGetId {
            it[userAuthId] = authId
            it[deviceUUID] = uuid
            it[deviceName] = name
            it[AuthDeviceTable.language] = language
            it[isSignedIn] = false
            it[updatedAt] = Clock.System.now()
        }?.value
    }

    override suspend fun updateLanguage(authId: Uuid, deviceUuid: Uuid, language: Language) = dbQuery<Unit> {
        AuthDeviceTable.update(
            where = { AuthDeviceTable.userAuthId.eq(authId).and(AuthDeviceTable.deviceUUID.eq(deviceUuid)) }
        ) {
            it[AuthDeviceTable.language] = language
            it[updatedAt] = Clock.System.now()
        }
    }

    override suspend fun deleteInactiveDevices(olderThan: Instant): List<Uuid> = dbQuery {
        AuthDeviceTable
            .deleteReturning(listOf(AuthDeviceTable.deviceUUID)) { AuthDeviceTable.lastLogInAt less olderThan }
            .map { it[AuthDeviceTable.deviceUUID] }
    }

    companion object {
        private val REFRESH_TOKEN_TTL = 7.days
    }
}