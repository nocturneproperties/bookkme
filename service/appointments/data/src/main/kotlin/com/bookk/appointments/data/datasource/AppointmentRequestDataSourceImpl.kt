package com.bookk.appointments.data.datasource

import com.bookk.appointments.data.orm.entity.AppointmentRequestEntity
import com.bookk.appointments.data.orm.entity.AppointmentRequestServiceEntity
import com.bookk.appointments.data.orm.entity.withDetails
import com.bookk.appointments.data.orm.table.AppointmentRequestServicesTable
import com.bookk.appointments.data.orm.table.AppointmentRequestTable
import com.bookk.appointments.domain.api.entity.AppointmentRequest
import com.bookk.appointments.domain.api.entity.AppointmentRequestHistoryFilter
import com.bookk.appointments.domain.api.entity.AppointmentRequestStatus
import com.bookk.appointments.domain.api.entity.BookingPeriod
import com.bookk.appointments.domain.api.entity.HistoryOrder
import com.bookk.appointments.domain.api.entity.OwnAppointmentRequestFilter
import com.bookk.appointments.domain.api.entity.PendingRequestRef
import com.bookk.appointments.domain.api.entity.StartBounds
import com.bookk.appointments.domain.datasource.AppointmentRequestDataSource
import com.bookk.core.data.DataSource
import com.bookk.core.data.Keyset
import com.bookk.core.data.cache.CacheClient
import com.bookk.core.data.cache.setIfAbsent
import com.bookk.core.domain.entity.Error
import com.bookk.core.domain.entity.Page
import com.bookk.core.domain.entity.PageCursor
import com.bookk.core.domain.entity.PageRequest
import com.bookk.core.domain.entity.toPage
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.compoundAnd
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
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
            .find { pendingOfUser(userId, businessId) }
            .toList()
            .decrypted()
    }

    override suspend fun getHistoryPage(
        businessId: Uuid,
        filter: AppointmentRequestHistoryFilter,
        starts: StartBounds,
        page: PageRequest,
    ): Page<AppointmentRequest> = dbQuery {
        val keyset = if (filter.order == HistoryOrder.SOONEST_FIRST) soonestFirst else newestFirst
        val conditions = listOfNotNull(
            AppointmentRequestTable.businessId eq businessId,
            filter.clientId?.let { AppointmentRequestTable.clientId eq it },
            filter.employeeId?.let { AppointmentRequestTable.employeeId eq it },
            filter.statuses.takeIf { it.isNotEmpty() }?.let { AppointmentRequestTable.status inList it },
            starts.from?.let { AppointmentRequestTable.dateStart greaterEq it },
            starts.until?.let { AppointmentRequestTable.dateStart less it },
        )
        pageOf(conditions, keyset, page)
    }

    override suspend fun getPageOfInitiator(
        initiatorUserId: Uuid,
        filter: OwnAppointmentRequestFilter,
        now: Instant,
        page: PageRequest,
    ): Page<AppointmentRequest> = dbQuery {
        val keyset = if (filter.period == BookingPeriod.UPCOMING) soonestFirst else newestFirst
        val conditions = listOfNotNull(
            AppointmentRequestTable.initiatorUserId eq initiatorUserId,
            filter.statuses.takeIf { it.isNotEmpty() }?.let { AppointmentRequestTable.status inList it },
            when (filter.period) {
                BookingPeriod.UPCOMING -> AppointmentRequestTable.dateStart greater now
                BookingPeriod.PAST -> AppointmentRequestTable.dateStart lessEq now
                null -> null
            },
        )
        pageOf(conditions, keyset, page)
    }

    override suspend fun getUpcomingPendingOfUser(
        userId: Uuid,
        startingAfter: Instant,
        limit: Int,
    ): List<AppointmentRequest> = dbQuery {
        AppointmentRequestEntity
            .find {
                (AppointmentRequestTable.initiatorUserId eq userId)
                    .and(AppointmentRequestTable.status eq AppointmentRequestStatus.PENDING)
                    .and(AppointmentRequestTable.dateStart greater startingAfter)
            }
            .orderBy(AppointmentRequestTable.dateStart to SortOrder.ASC)
            .limit(limit)
            .toList()
            .withDetails()
            .decrypted()
    }

    override suspend fun countPendingByUser(userId: Uuid, businessId: Uuid): Long = dbQuery {
        AppointmentRequestTable.selectAll().where { pendingOfUser(userId, businessId) }.count()
    }

    override suspend fun create(request: AppointmentRequest): AppointmentRequest = dbQuery {
        val requestEntity = AppointmentRequestEntity.new(request, snapshotCipher.encrypt(request))
        request.services.forEach {
            AppointmentRequestServiceEntity.new(requestEntity.id, it)
        }
        request.copy(id = requestEntity.id.value, businessName = requestEntity.business.name)
    }

    override suspend fun update(request: AppointmentRequest): AppointmentRequest = dbQuery {
        val stored = AppointmentRequestEntity.findById(request.id) ?: throw Error.NotFound()
        val read = snapshotCipher.read(stored.storedSnapshot(), AppointmentRequestTable.tableName)
        val requestEntity = AppointmentRequestEntity.findByIdAndUpdate(
            request,
            snapshotCipher.encryptChanges(request, read),
        ) ?: throw Error.NotFound()
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

    override suspend fun withdraw(id: Uuid) = dbQuery<Unit> {
        AppointmentRequestEntity.findByIdAndUpdate(id) {
            it.status = AppointmentRequestStatus.CANCELLED
            it.updatedAt = Clock.System.now()
        } ?: throw Error.NotFound()
    }

    override suspend fun cancelOutdated(before: Instant): List<PendingRequestRef> = dbQuery {
        val outdated = pendingRefsForUpdate(AppointmentRequestTable.dateEnd less before)
        if (outdated.isNotEmpty()) {
            AppointmentRequestTable.update(where = { AppointmentRequestTable.id inList outdated.map { it.id } }) {
                it[AppointmentRequestTable.status] = AppointmentRequestStatus.CANCELLED
                it[AppointmentRequestTable.updatedAt] = Clock.System.now()
            }
        }
        outdated
    }

    override suspend fun getPendingOfClientUserForUpdate(clientUserId: Uuid): List<PendingRequestRef> = dbQuery {
        pendingRefsForUpdate(
            (AppointmentRequestTable.clientUserId eq clientUserId) and
                (AppointmentRequestTable.employeeUserId neq clientUserId),
        )
    }

    override suspend fun getPendingOfClientForUpdate(
        businessId: Uuid,
        clientId: Uuid,
    ): List<PendingRequestRef> = dbQuery {
        pendingRefsForUpdate(
            (AppointmentRequestTable.businessId eq businessId) and (AppointmentRequestTable.clientId eq clientId),
        )
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
            val snapshots = snapshotCipher.decryptAll(
                moved.map {
                    it.storedSnapshot()
                },
                AppointmentRequestTable.tableName,
            )
            val clients = snapshotCipher.reencryptClients(snapshots, toClientId)
            moved.zip(clients).forEach { (entity, client) ->
                AppointmentRequestEntity.findByIdAndUpdateClient(entity.id.value, toClientId, client)
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

    private suspend fun AppointmentRequestEntity.decrypted(): AppointmentRequest {
        return domain(snapshotCipher.decrypt(storedSnapshot(), AppointmentRequestTable.tableName))
    }

    private suspend fun List<AppointmentRequestEntity>.decrypted(): List<AppointmentRequest> {
        val snapshots = snapshotCipher.decryptAll(map { it.storedSnapshot() }, AppointmentRequestTable.tableName)
        return zip(snapshots) { entity, snapshot -> entity.domain(snapshot) }
    }

    private fun pendingRefsForUpdate(condition: Op<Boolean>): List<PendingRequestRef> {
        return AppointmentRequestTable.select(AppointmentRequestTable.id, AppointmentRequestTable.employeeUserId)
            .where { (AppointmentRequestTable.status eq AppointmentRequestStatus.PENDING) and condition }
            .orderBy(AppointmentRequestTable.id)
            .forUpdate()
            .map { PendingRequestRef(it[AppointmentRequestTable.id].value, it[AppointmentRequestTable.employeeUserId]) }
    }

    private fun pendingOfUser(userId: Uuid, businessId: Uuid): Op<Boolean> {
        return AppointmentRequestTable.initiatorUserId.eq(userId)
            .and(AppointmentRequestTable.businessId.eq(businessId))
            .and(AppointmentRequestTable.status.eq(AppointmentRequestStatus.PENDING))
    }

    private suspend fun pageOf(
        conditions: List<Op<Boolean>>,
        keyset: Keyset,
        page: PageRequest,
    ): Page<AppointmentRequest> {
        val entities = AppointmentRequestEntity.find { (conditions + keyset.after(page.after)).compoundAnd() }
            .orderBy(*keyset.orderBy())
            .limit(page.fetchSize)
            .toList()
            .toPage(page) { PageCursor(after = it.dateStart, id = it.id.value) }
        return Page(entities.items.withDetails().decrypted(), entities.next)
    }

    private companion object {
        val newestFirst = Keyset(AppointmentRequestTable.dateStart, AppointmentRequestTable.id, SortOrder.DESC)
        val soonestFirst = Keyset(AppointmentRequestTable.dateStart, AppointmentRequestTable.id, SortOrder.ASC)
    }
}
