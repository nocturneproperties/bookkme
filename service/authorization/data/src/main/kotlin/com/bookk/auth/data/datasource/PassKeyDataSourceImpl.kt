package com.bookk.auth.data.datasource

import com.bookk.auth.data.map.toDomain
import com.bookk.auth.data.orm.entity.PasskeyCredentialEntity
import com.bookk.auth.data.orm.table.AuthenticationTable
import com.bookk.auth.data.orm.table.PasskeyCredentialTable
import com.bookk.auth.domain.api.identification.entity.PasskeyCredential
import com.bookk.auth.domain.datasource.PassKeyDataSource
import com.bookk.core.data.DataSource
import com.bookk.core.data.cache.CacheClient
import com.bookk.core.data.cache.get
import com.bookk.core.data.cache.set
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.count
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.innerJoin
import org.jetbrains.exposed.v1.core.longLiteral
import org.jetbrains.exposed.v1.core.wrapAsExpression
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

internal class PassKeyDataSourceImpl(
    private val cacheClient: CacheClient<String>
) : DataSource(), PassKeyDataSource {

    override suspend fun saveChallengeToCache(requestId: String, challenge: String) = mapExceptions {
        cacheClient.set(requestId, challenge, 5.minutes)
    }

    override suspend fun getCachedChallenge(requestId: String): String? {
        return mapExceptions { cacheClient.get(requestId) }
    }

    override suspend fun deleteCachedChallenge(requestId: String) {
        mapExceptions { cacheClient.delete(requestId) }
    }

    override suspend fun createPasskeyCredential(credential: PasskeyCredential) = dbQuery<Unit> {
        PasskeyCredentialTable.insert {
            it[authId] = credential.authId
            it[name] = credential.name
            it[credDescriptorId] = credential.credDescriptor.id
            it[credDescriptorType] = credential.credDescriptor.type
            it[credDescriptorTransports] = credential.credDescriptor.transports.joinToString(separator = ",") { it }
            it[publicKey] = credential.publicKey
            it[signatureCount] = credential.signatureCount
            it[isDiscoverable] = credential.isDiscoverable
            it[isBackupEligible] = credential.isBackupEligible
            it[isBackedUp] = credential.isBackedUp
            it[attestationObject] = credential.attestationObject
            it[clientDataJson] = credential.clientData
            it[updatedAt] = Clock.System.now()
        }
    }

    override suspend fun getCredentialBy(userHandle: Uuid, credentialId: ByteArray): PasskeyCredential? = dbQuery {
        PasskeyCredentialTable
            .innerJoin(
                AuthenticationTable,
                onColumn = { authId },
                otherColumn = { id },
                additionalConstraint = { AuthenticationTable.uuid eq userHandle }
            )
            .selectAll()
            .where { PasskeyCredentialTable.credDescriptorId eq credentialId }
            .map { PasskeyCredentialEntity.wrapRow(it).toDomain() }
            .singleOrNull()
    }

    override suspend fun getCredentialBy(authId: Uuid): List<PasskeyCredential> = dbQuery {
        AuthenticationTable
            .innerJoin(
                otherTable = PasskeyCredentialTable,
                onColumn = { id },
                otherColumn = { this.authId },
            )
            .select(PasskeyCredentialTable.columns)
            .where { AuthenticationTable.id eq authId }
            .map { PasskeyCredentialEntity.wrapRow(it).toDomain() }
            .toList()
    }

    override suspend fun getUsernameByHandle(userHandle: Uuid): String? = dbQuery {
        val exists = AuthenticationTable
            .select(AuthenticationTable.id)
            .where { AuthenticationTable.uuid eq userHandle }
            .empty()
            .not()

        userHandle.toString().takeIf { exists }
    }

    override suspend fun getCredentialsByUsername(username: Uuid): Set<PasskeyCredential> = dbQuery {
        PasskeyCredentialTable
            .innerJoin(
                otherTable = AuthenticationTable,
                onColumn = { authId },
                otherColumn = { id },
                additionalConstraint = { AuthenticationTable.uuid eq username }
            )
            .selectAll()
            .map { PasskeyCredentialEntity.wrapRow(it).toDomain() }
            .toSet()
    }

    override suspend fun getCredentialsByCredentialId(credentialId: ByteArray): Set<PasskeyCredential> = dbQuery {
        PasskeyCredentialTable
            .innerJoin(
                otherTable = AuthenticationTable,
                onColumn = { authId },
                otherColumn = { id },
            )
            .selectAll()
            .where { PasskeyCredentialTable.credDescriptorId eq credentialId }
            .map { PasskeyCredentialEntity.wrapRow(it).toDomain() }
            .toSet()
    }

    override suspend fun getHandleByUsername(username: Uuid): Uuid? = dbQuery {
        val exists = AuthenticationTable
            .select(AuthenticationTable.id)
            .where { AuthenticationTable.uuid eq username }
            .empty()
            .not()

        username.takeIf { exists }
    }

    override suspend fun markAsUsed(passkeyCredentialId: Uuid) = dbQuery<Unit> {
        PasskeyCredentialTable.update(where = { PasskeyCredentialTable.id eq passkeyCredentialId }) {
            it[lastUsedAt] = Clock.System.now()
        }
    }

    override suspend fun deletePasskey(id: Uuid, authId: Uuid): Int = dbQuery {
        val existingPasskeys = PasskeyCredentialTable
            .select(PasskeyCredentialTable.id.count())
            .where { PasskeyCredentialTable.authId eq authId }
            .let { wrapAsExpression<Long>(it) }
        PasskeyCredentialTable.deleteWhere {
            (PasskeyCredentialTable.id eq id) and (existingPasskeys greater longLiteral(1L))
        }
    }
}