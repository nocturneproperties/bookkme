package com.bookk.appointments.data.datasource

import com.bookk.appointments.data.orm.entity.AppointmentEntity
import com.bookk.appointments.data.orm.entity.AppointmentServiceEntity
import com.bookk.appointments.data.orm.entity.withDetails
import com.bookk.appointments.data.orm.table.AppointmentBusinessTable
import com.bookk.appointments.data.orm.table.AppointmentServicesTable
import com.bookk.appointments.data.orm.table.AppointmentTable
import com.bookk.appointments.data.orm.table.SettingsTable
import com.bookk.appointments.domain.api.entity.Appointment
import com.bookk.appointments.domain.api.entity.AppointmentCompletedBy
import com.bookk.appointments.domain.api.entity.AppointmentHistoryFilter
import com.bookk.appointments.domain.api.entity.AppointmentRequest
import com.bookk.appointments.domain.api.entity.AppointmentStatus
import com.bookk.appointments.domain.api.entity.BookedRange
import com.bookk.appointments.domain.api.entity.BookingPeriod
import com.bookk.appointments.domain.api.entity.HistoryOrder
import com.bookk.appointments.domain.api.entity.OwnAppointmentFilter
import com.bookk.appointments.domain.api.entity.PriceAdjustment
import com.bookk.appointments.domain.api.entity.StartBounds
import com.bookk.appointments.domain.datasource.AppointmentDataSource
import com.bookk.core.data.DataSource
import com.bookk.core.data.Keyset
import com.bookk.core.domain.entity.Error
import com.bookk.core.domain.entity.Page
import com.bookk.core.domain.entity.PageCursor
import com.bookk.core.domain.entity.PageRequest
import com.bookk.core.domain.entity.toPage
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.inSubQuery
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.core.statements.UpdateStatement
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class AppointmentDataSourceImpl(
    private val snapshotCipher: SnapshotCipher,
) : DataSource(), AppointmentDataSource {

    override suspend fun get(id: Uuid): Appointment = dbQuery {
        AppointmentEntity.findById(id)
            ?.decrypted() ?: throw Error.NotFound()
    }

    override suspend fun getForUpdate(id: Uuid): Appointment = dbQuery {
        AppointmentEntity.find { AppointmentTable.id eq id }
            .forUpdate()
            .singleOrNull()
            ?.decrypted() ?: throw Error.NotFound()
    }

    override suspend fun getAll(businessId: Uuid): List<Appointment> = dbQuery {
        AppointmentEntity
            .find { AppointmentTable.businessId eq businessId }
            .toList()
            .withDetails()
            .decrypted()
    }

    override suspend fun getAllByIds(ids: Collection<Uuid>): List<Appointment> {
        if (ids.isEmpty()) return emptyList()
        return dbQuery {
            AppointmentEntity.find { AppointmentTable.id inList ids }
                .toList()
                .withDetails()
                .decrypted()
        }
    }

    override suspend fun getUpcomingOfClientUser(
        clientUserId: Uuid,
        endingAfter: Instant,
        limit: Int,
    ): List<Appointment> = dbQuery {
        AppointmentEntity
            .find {
                (AppointmentTable.clientUserId eq clientUserId)
                    .and(AppointmentTable.status eq AppointmentStatus.SCHEDULED)
                    .and(AppointmentTable.dateEnd greater endingAfter)
            }
            .orderBy(AppointmentTable.dateStart to SortOrder.ASC)
            .limit(limit)
            .toList()
            .withDetails()
            .decrypted()
    }

    override suspend fun getPageOfClientUser(
        clientUserId: Uuid,
        filter: OwnAppointmentFilter,
        now: Instant,
        page: PageRequest,
    ): Page<Appointment> = dbQuery {
        val keyset = if (filter.period == BookingPeriod.UPCOMING) soonestFirst else newestFirst
        val conditions = listOfNotNull(
            AppointmentTable.clientUserId eq clientUserId,
            filter.statuses.takeIf { it.isNotEmpty() }?.let { AppointmentTable.status inList it },
            when (filter.period) {
                BookingPeriod.UPCOMING -> AppointmentTable.dateEnd greater now
                BookingPeriod.PAST -> AppointmentTable.dateEnd lessEq now
                null -> null
            },
            keyset.after(page.after),
        )
        val entities = AppointmentEntity.find { conditions.reduce { acc, condition -> acc and condition } }
            .orderBy(*keyset.orderBy())
            .limit(page.fetchSize)
            .toList()
            .toPage(page) { PageCursor(after = it.dateStart, id = it.id.value) }
        Page(entities.items.withDetails().decrypted(), entities.next)
    }

    override suspend fun getAllForDate(
        businessId: Uuid,
        range: ClosedRange<Instant>,
        employeeId: Uuid?,
    ): List<Appointment> = dbQuery {
        val withinRange = AppointmentTable.businessId.eq(businessId)
            .and(AppointmentTable.dateStart.greaterEq(range.start))
            .and(AppointmentTable.dateEnd.lessEq(range.endInclusive))
        AppointmentEntity
            .find {
                employeeId?.let { withinRange.and(AppointmentTable.employeeId.eq(it)) } ?: withinRange
            }
            .orderBy(AppointmentTable.dateStart to SortOrder.ASC)
            .toList()
            .withDetails()
            .decrypted()
    }

    override suspend fun getHistoryPage(
        businessId: Uuid,
        filter: AppointmentHistoryFilter,
        starts: StartBounds,
        page: PageRequest,
    ): Page<Appointment> = dbQuery {
        val keyset = if (filter.order == HistoryOrder.SOONEST_FIRST) soonestFirst else newestFirst
        val query = filter.query?.takeIf { it.isNotBlank() }?.lowercase()
        val conditions = listOfNotNull(
            AppointmentTable.businessId eq businessId,
            filter.clientId?.let { AppointmentTable.clientId eq it },
            filter.employeeId?.let { AppointmentTable.employeeId eq it },
            filter.statuses.takeIf { it.isNotEmpty() }?.let { AppointmentTable.status inList it },
            starts.from?.let { AppointmentTable.dateStart greaterEq it },
            starts.until?.let { AppointmentTable.dateStart less it },
            query?.let { matching(businessId, it) },
            keyset.after(page.after),
        )
        val entities = AppointmentEntity.find { conditions.reduce { acc, condition -> acc and condition } }
            .orderBy(*keyset.orderBy())
            .limit(page.fetchSize)
            .toList()
            .toPage(page) { PageCursor(after = it.dateStart, id = it.id.value) }
        Page(entities.items.withDetails().decrypted(), entities.next)
    }

    private fun matching(businessId: Uuid, query: String): Op<Boolean> {
        val serviceMatches = (AppointmentServicesTable innerJoin AppointmentTable)
            .select(AppointmentServicesTable.appointmentId)
            .where {
                (AppointmentTable.businessId eq businessId) and
                    (AppointmentServicesTable.serviceName.lowerCase() like "%$query%")
            }
        val matchesService = AppointmentTable.id inSubQuery serviceMatches
        val matchesClientName = AppointmentTable.clientNameIndex eq snapshotCipher.clientNameIndex(query)
        return matchesService or matchesClientName
    }

    override suspend fun getEmployeeBookings(
        businessId: Uuid,
        employeeId: Uuid,
        overlapping: OpenEndRange<Instant>,
    ): List<BookedRange> = dbQuery {
        AppointmentTable.select(AppointmentTable.id, AppointmentTable.dateStart, AppointmentTable.dateEnd)
            .where {
                (AppointmentTable.employeeId eq employeeId)
                    .and(AppointmentTable.businessId eq businessId)
                    .and(AppointmentTable.dateStart.less(overlapping.endExclusive))
                    .and(AppointmentTable.dateEnd.greater(overlapping.start))
                    .and(AppointmentTable.status eq AppointmentStatus.SCHEDULED)
            }
            .map {
                BookedRange(
                    id = it[AppointmentTable.id].value,
                    start = it[AppointmentTable.dateStart],
                    end = it[AppointmentTable.dateEnd],
                )
            }
    }

    override suspend fun create(request: AppointmentRequest): Appointment = dbQuery {
        val encrypted = snapshotCipher.encryptAppointmentOf(request)
        val appointment = AppointmentEntity.new(request, encrypted)
        request.services.forEach {
            AppointmentServiceEntity.new(appointment.id, it)
        }
        appointment.decrypted()
    }

    override suspend fun create(appointment: Appointment): Appointment = dbQuery {
        val encrypted = snapshotCipher.encrypt(appointment)
        val appointmentEntity = AppointmentEntity.new(appointment, encrypted)
        appointment.services.forEach {
            AppointmentServiceEntity.new(appointmentEntity.id, it)
        }
        appointment.copy(id = appointmentEntity.id.value)
    }

    override suspend fun update(appointment: Appointment): Appointment = dbQuery {
        val stored = AppointmentEntity.findById(appointment.id) ?: throw Error.NotFound()
        val read = snapshotCipher.read(stored.storedSnapshot(), AppointmentTable.tableName)
        val encrypted = snapshotCipher.encryptChanges(appointment, read)
        val appointmentEntity = AppointmentEntity.findByIdAndUpdate(appointment, encrypted) ?: throw Error.NotFound()
        AppointmentServicesTable.deleteWhere {
            AppointmentServicesTable.appointmentId eq appointment.id
        }
        appointment.services.forEach {
            AppointmentServiceEntity.new(appointmentEntity.id, it)
        }
        appointmentEntity.decrypted()
    }

    override suspend fun delete(id: Uuid) = dbQuery<Unit> {
        AppointmentTable.deleteWhere {
            AppointmentTable.id eq id
        }
    }

    override suspend fun cancel(id: Uuid, reason: String): Appointment = dbQuery {
        AppointmentEntity.findByIdAndUpdate(id) {
            it.status = AppointmentStatus.CANCELLED
            it.completedBy = null
            it.cancellationReason = reason
            it.updatedAt = Clock.System.now()
        }?.decrypted() ?: throw Error.NotFound()
    }

    override suspend fun cancelAll(ids: Collection<Uuid>, reason: String) {
        if (ids.isEmpty()) return
        dbQuery {
            AppointmentTable.update(where = { AppointmentTable.id inList ids }) {
                it[status] = AppointmentStatus.CANCELLED
                it[cancellationReason] = reason
                it[updatedAt] = Clock.System.now()
            }
        }
    }

    override suspend fun markCompleted(now: Instant) = dbQuery<Unit> {
        (SettingsTable innerJoin AppointmentBusinessTable)
            .select(SettingsTable.businessId, AppointmentBusinessTable.timeZone)
            .where { SettingsTable.automaticCompletion eq true }
            .toList()
            .groupBy({ it[AppointmentBusinessTable.timeZone] }, { it[SettingsTable.businessId] })
            .forEach { (timeZoneId, businessIds) ->
                AppointmentTable.update(
                    where = {
                        AppointmentTable.status.eq(AppointmentStatus.SCHEDULED)
                            .and(AppointmentTable.dateEnd.less(now.startOfLocalDay(TimeZone.of(timeZoneId))))
                            .and(AppointmentTable.businessId.inList(businessIds))
                    },
                ) {
                    it[AppointmentTable.status] = AppointmentStatus.COMPLETED
                    it[AppointmentTable.completedBy] = AppointmentCompletedBy.SYSTEM
                    it[AppointmentTable.updatedAt] = now
                }
            }
    }

    private fun Instant.startOfLocalDay(timeZone: TimeZone): Instant {
        return toLocalDateTime(timeZone).date.atStartOfDayIn(timeZone)
    }

    override suspend fun markUnresolved(before: Instant) = dbQuery<Unit> {
        AppointmentTable.update(
            where = {
                AppointmentTable.status.eq(AppointmentStatus.SCHEDULED)
                    .and(AppointmentTable.dateEnd.less(before))
            },
        ) {
            it[AppointmentTable.status] = AppointmentStatus.UNRESOLVED
            it[AppointmentTable.completedBy] = null
            it[AppointmentTable.updatedAt] = Clock.System.now()
        }
    }

    override suspend fun markCompletedByUser(id: Uuid): Appointment = dbQuery {
        AppointmentEntity.findByIdAndUpdate(id) {
            it.status = AppointmentStatus.COMPLETED
            it.completedBy = AppointmentCompletedBy.USER
            it.updatedAt = Clock.System.now()
        }?.decrypted() ?: throw Error.NotFound()
    }

    override suspend fun reopen(id: Uuid) = dbQuery<Unit> {
        val appointment = AppointmentEntity.findByIdAndUpdate(id) {
            it.status = AppointmentStatus.SCHEDULED
            it.completedBy = null
            it.updatedAt = Clock.System.now()
        } ?: throw Error.NotFound()
        appointment.clearPriceAdjustment()
    }

    override suspend fun adjustPrice(id: Uuid, adjustment: PriceAdjustment): Appointment = dbQuery {
        val appointment = AppointmentEntity.findById(id) ?: throw Error.NotFound()
        appointment.replacePriceAdjustment(adjustment)
        appointment.decrypted()
    }

    override suspend fun markNoShow(id: Uuid): Appointment = dbQuery {
        AppointmentEntity.findByIdAndUpdate(id) {
            it.status = AppointmentStatus.NO_SHOW
            it.completedBy = null
            it.updatedAt = Clock.System.now()
        }?.decrypted() ?: throw Error.NotFound()
    }

    override suspend fun unlinkInitiator(initiatorUserId: Uuid) = dbQuery<Unit> {
        AppointmentTable.update(where = { AppointmentTable.initiatorUserId eq initiatorUserId }) {
            it[AppointmentTable.initiatorUserId] = Uuid.NIL
        }
    }

    override suspend fun getClientIdsOfUserForUpdate(clientUserId: Uuid): Set<Uuid> = dbQuery {
        AppointmentTable.select(AppointmentTable.clientId)
            .where { AppointmentTable.clientUserId eq clientUserId }
            .forUpdate()
            .map { it[AppointmentTable.clientId] }
            .toSet()
    }

    override suspend fun anonymizeClientUser(clientUserId: Uuid) = dbQuery<Unit> {
        AppointmentTable.update(where = { AppointmentTable.clientUserId eq clientUserId }) {
            it.eraseClientDetails()
        }
    }

    override suspend fun getUpcomingIdsOfUserForUpdate(userId: Uuid, startingAfter: Instant): List<Uuid> = dbQuery {
        AppointmentTable.select(AppointmentTable.id)
            .where {
                ((AppointmentTable.clientUserId eq userId) or (AppointmentTable.employeeUserId eq userId))
                    .and(AppointmentTable.status eq AppointmentStatus.SCHEDULED)
                    .and(AppointmentTable.dateStart greater startingAfter)
            }
            .forUpdate()
            .map { it[AppointmentTable.id].value }
    }

    override suspend fun reassignClients(businessId: Uuid, fromClientIds: Collection<Uuid>, toClientId: Uuid) {
        if (fromClientIds.isEmpty()) return
        dbQuery {
            val moved = AppointmentEntity
                .find {
                    (AppointmentTable.businessId eq businessId) and (AppointmentTable.clientId inList fromClientIds)
                }
                .forUpdate()
                .toList()
            val snapshots = snapshotCipher.decryptAll(moved.map { it.storedSnapshot() }, AppointmentTable.tableName)
            val clients = snapshotCipher.reencryptClients(snapshots, toClientId)
            moved.zip(clients).forEach { (entity, client) ->
                AppointmentEntity.findByIdAndUpdateClient(entity.id.value, toClientId, client)
            }
        }
    }

    override suspend fun linkClientUser(businessId: Uuid, clientId: Uuid, userId: Uuid) = dbQuery<Unit> {
        AppointmentTable.update(where = {
            (AppointmentTable.businessId eq businessId) and
                (AppointmentTable.clientId eq clientId)
        }) {
            it[clientUserId] = userId
        }
    }

    override suspend fun getUpcomingIdsOfClientForUpdate(
        businessId: Uuid,
        clientId: Uuid,
        startingAfter: Instant,
    ): List<Uuid> = dbQuery {
        AppointmentTable.select(AppointmentTable.id)
            .where {
                (AppointmentTable.businessId eq businessId)
                    .and(AppointmentTable.clientId eq clientId)
                    .and(AppointmentTable.status eq AppointmentStatus.SCHEDULED)
                    .and(AppointmentTable.dateStart greater startingAfter)
            }
            .forUpdate()
            .map { it[AppointmentTable.id].value }
    }

    override suspend fun anonymizeClient(businessId: Uuid, clientId: Uuid) = dbQuery<Unit> {
        AppointmentTable.update(where = {
            (AppointmentTable.businessId eq businessId) and
                (AppointmentTable.clientId eq clientId)
        }) {
            it.eraseClientDetails()
        }
    }

    private fun UpdateStatement.eraseClientDetails() {
        this[AppointmentTable.clientNameEncrypted] = null
        this[AppointmentTable.clientPhoneEncrypted] = null
        this[AppointmentTable.clientEmailEncrypted] = null
        this[AppointmentTable.noteEncrypted] = null
        this[AppointmentTable.clientNoteEncrypted] = null
        this[AppointmentTable.clientNameIndex] = null
        this[AppointmentTable.clientUserId] = null
    }

    private suspend fun AppointmentEntity.decrypted(): Appointment {
        return domain(snapshotCipher.decrypt(storedSnapshot(), AppointmentTable.tableName))
    }

    private suspend fun List<AppointmentEntity>.decrypted(): List<Appointment> {
        val snapshots = snapshotCipher.decryptAll(map { it.storedSnapshot() }, AppointmentTable.tableName)
        return zip(snapshots) { entity, snapshot -> entity.domain(snapshot) }
    }

    private companion object {
        val newestFirst = Keyset(AppointmentTable.dateStart, AppointmentTable.id, SortOrder.DESC)
        val soonestFirst = Keyset(AppointmentTable.dateStart, AppointmentTable.id, SortOrder.ASC)
    }
}
