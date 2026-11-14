package com.bookk.appointments.data.datasource

import com.bookk.appointments.data.orm.entity.AppointmentRequestEntity
import com.bookk.appointments.data.orm.entity.AppointmentRequestServiceEntity
import com.bookk.appointments.data.orm.entity.ClientDetails
import com.bookk.appointments.data.orm.entity.EncryptedSnapshot
import com.bookk.appointments.data.orm.table.AppointmentRequestServicesTable
import com.bookk.appointments.data.orm.table.AppointmentRequestTable
import com.bookk.appointments.domain.api.entity.AppointmentRequest
import com.bookk.appointments.domain.api.entity.AppointmentRequestStatus
import com.bookk.appointments.domain.datasource.AppointmentRequestDataSource
import com.bookk.core.data.DataSource
import com.bookk.core.data.cache.CacheClient
import com.bookk.core.data.cache.setIfAbsent
import com.bookk.core.domain.entity.Error
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class AppointmentRequestDataSourceImpl(
    private val cacheClient: CacheClient<String>,
    private val snapshotCipher: SnapshotCipher,
) : DataSource(), AppointmentRequestDataSource {
    override suspend fun get(id: Uuid): AppointmentRequest? = dbQuery {
        AppointmentRequestEntity.findById(id)
            ?.decrypted()
    }

    override suspend fun getForUpdate(id: Uuid): AppointmentRequest? = dbQuery {
        AppointmentRequestEntity.find { AppointmentRequestTable.id eq id }
            .forUpdate()
            .singleOrNull()
            ?.decrypted()
    }

    override suspend fun getPending(businessId: Uuid): List<AppointmentRequest> = dbQuery {
        AppointmentRequestEntity
            .find {
                AppointmentRequestTable.businessId.eq(businessId)
                    .and(AppointmentRequestTable.status.eq(AppointmentRequestStatus.PENDING))
            }
            .toList()
            .decrypted()
    }

    override suspend fun getPendingByUser(userId: Uuid, businessId: Uuid): List<AppointmentRequest> = dbQuery {
        AppointmentRequestEntity
            .find {
                AppointmentRequestTable.initiatorUserId.eq(userId)
                    .and(AppointmentRequestTable.businessId.eq(businessId))
                    .and(AppointmentRequestTable.status.eq(AppointmentRequestStatus.PENDING))
            }
            .toList()
            .decrypted()
    }

    override suspend fun create(request: AppointmentRequest): AppointmentRequest = dbQuery {
        val requestEntity = AppointmentRequestEntity.new(request, encrypt(request))
        request.services.forEach {
            AppointmentRequestServiceEntity.new(requestEntity.id, it)
        }
        request.copy(id = requestEntity.id.value)
    }

    override suspend fun update(request: AppointmentRequest): AppointmentRequest = dbQuery {
        val requestEntity =
            AppointmentRequestEntity.findByIdAndUpdate(request, encrypt(request)) ?: throw Error.NotFound()
        AppointmentRequestServicesTable.deleteWhere {
            AppointmentRequestServicesTable.requestId eq request.id
        }
        request.services.forEach {
            AppointmentRequestServiceEntity.new(requestEntity.id, it)
        }
        request
    }

    override suspend fun delete(request: AppointmentRequest) = dbQuery<Unit> {
        AppointmentRequestTable.deleteWhere {
            AppointmentRequestTable.id eq request.id
        }
    }

    override suspend fun approve(request: AppointmentRequest) = dbQuery<Unit> {
        AppointmentRequestTable.update(
            where = { AppointmentRequestTable.id eq request.id },
        ) {
            it[AppointmentRequestTable.status] = AppointmentRequestStatus.APPROVED
            it[AppointmentRequestTable.updatedAt] = Clock.System.now()
        }
    }

    override suspend fun decline(id: Uuid, reason: String) = dbQuery<AppointmentRequest> {
        AppointmentRequestEntity.findByIdAndUpdate(id) {
            it.status = AppointmentRequestStatus.DECLINED
            it.declineReason = reason
            it.updatedAt = Clock.System.now()
        }?.decrypted() ?: throw Error.NotFound()
    }

    override suspend fun hasOverlapsWith(request: AppointmentRequest): Boolean = dbQuery {
        AppointmentRequestTable.select(AppointmentRequestTable.id)
            .where {
                (AppointmentRequestTable.businessId eq request.businessId)
                    .and(AppointmentRequestTable.initiatorUserId eq request.initiatorUserId)
                    .and(AppointmentRequestTable.dateStart.less(request.dateEnd))
                    .and(AppointmentRequestTable.dateEnd.greater(request.date))
                    .and(AppointmentRequestTable.status eq AppointmentRequestStatus.PENDING)
            }
            .limit(1)
            .empty()
            .not()
    }

    override suspend fun cancelOutdated(before: Instant) = dbQuery<Unit> {
        AppointmentRequestTable.update(
            where = {
                AppointmentRequestTable.status.eq(AppointmentRequestStatus.PENDING)
                    .and(AppointmentRequestTable.dateEnd.less(before))
            },
        ) {
            it[AppointmentRequestTable.status] = AppointmentRequestStatus.CANCELLED
            it[AppointmentRequestTable.updatedAt] = Clock.System.now()
        }
    }

    override suspend fun getClientIdsOfUserForUpdate(clientUserId: Uuid): Set<Uuid> = dbQuery {
        AppointmentRequestTable.select(AppointmentRequestTable.clientId)
            .where { AppointmentRequestTable.clientUserId eq clientUserId }
            .forUpdate()
            .map { it[AppointmentRequestTable.clientId] }
            .toSet()
    }

    override suspend fun deleteForUser(userId: Uuid) = dbQuery<Unit> {
        AppointmentRequestTable.deleteWhere {
            (AppointmentRequestTable.clientUserId eq userId) or (AppointmentRequestTable.employeeUserId eq userId)
        }
        AppointmentRequestTable.update(where = { AppointmentRequestTable.initiatorUserId eq userId }) {
            it[initiatorUserId] = Uuid.NIL
        }
    }

    override suspend fun reserveOfferToken(token: String): Boolean = mapExceptions {
        cacheClient.setIfAbsent(token, "marker", 10.minutes)
    }

    override suspend fun releaseOfferToken(token: String) = mapExceptions {
        cacheClient.delete(token)
    }

    override suspend fun reassignClients(businessId: Uuid, fromClientIds: Collection<Uuid>, toClientId: Uuid) {
        if (fromClientIds.isEmpty()) return
        dbQuery {
            val moved = AppointmentRequestEntity
                .find {
                    (AppointmentRequestTable.businessId eq businessId) and
                        (AppointmentRequestTable.clientId inList fromClientIds)
                }
                .forUpdate()
                .toList()
            val snapshots = moved.associateWith { it.storedSnapshot() }
            val reader = snapshotCipher.reader(snapshots.values, AppointmentRequestTable.tableName)
            snapshots.forEach { (entity, snapshot) ->
                val client = snapshotCipher.reencryptClient(snapshot, reader, toClientId)
                entity.clientId = toClientId
                entity.storeClient(client)
            }
        }
    }

    override suspend fun linkClientUser(businessId: Uuid, clientId: Uuid, userId: Uuid) = dbQuery<Unit> {
        AppointmentRequestTable.update(where = {
            (AppointmentRequestTable.businessId eq businessId) and
                (AppointmentRequestTable.clientId eq clientId)
        }) {
            it[clientUserId] = userId
        }
    }

    override suspend fun deleteForClient(businessId: Uuid, clientId: Uuid) = dbQuery<Unit> {
        AppointmentRequestTable.deleteWhere {
            (AppointmentRequestTable.businessId eq businessId) and (AppointmentRequestTable.clientId eq clientId)
        }
    }

    override suspend fun deleteEndedBefore(before: Instant) = dbQuery<Unit> {
        AppointmentRequestTable.deleteWhere { AppointmentRequestTable.dateEnd less before }
    }

    override suspend fun encryptPlaintextRequests(limit: Int): Int = dbQuery {
        val plaintext = AppointmentRequestEntity
            .find { AppointmentRequestTable.clientName.isNotNull() or AppointmentRequestTable.employeeName.isNotNull() }
            .limit(limit)
            .forUpdate()
            .toList()
        plaintext.forEach { it.store(snapshotCipher.encryptPlaintext(it.storedSnapshot())) }
        plaintext.size
    }

    private suspend fun encrypt(request: AppointmentRequest): EncryptedSnapshot {
        val client = request.client
        val details =
            ClientDetails(fullName = client.fullName, phone = client.phone, email = client.email, note = request.note)
        return snapshotCipher.encrypt(client.id, details, request.employee.userId, request.employee.fullName)
    }

    private suspend fun AppointmentRequestEntity.decrypted(): AppointmentRequest {
        return listOf(this).decrypted().single()
    }

    private suspend fun List<AppointmentRequestEntity>.decrypted(): List<AppointmentRequest> {
        val reader = snapshotCipher.reader(map { it.storedSnapshot() }, AppointmentRequestTable.tableName)
        return map { it.domain(reader) }
    }
}
