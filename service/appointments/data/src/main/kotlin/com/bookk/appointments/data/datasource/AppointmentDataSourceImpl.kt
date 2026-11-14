package com.bookk.appointments.data.datasource

import com.bookk.appointments.data.orm.entity.AppointmentEntity
import com.bookk.appointments.data.orm.entity.AppointmentServiceEntity
import com.bookk.appointments.data.orm.entity.ClientDetails
import com.bookk.appointments.data.orm.entity.EncryptedSnapshot
import com.bookk.appointments.data.orm.entity.withDetails
import com.bookk.appointments.data.orm.table.AppointmentBusinessTable
import com.bookk.appointments.data.orm.table.AppointmentServicesTable
import com.bookk.appointments.data.orm.table.AppointmentTable
import com.bookk.appointments.data.orm.table.SettingsTable
import com.bookk.appointments.domain.api.entity.Appointment
import com.bookk.appointments.domain.api.entity.AppointmentCompletedBy
import com.bookk.appointments.domain.api.entity.AppointmentPagination
import com.bookk.appointments.domain.api.entity.AppointmentRepresentation
import com.bookk.appointments.domain.api.entity.AppointmentRequest
import com.bookk.appointments.domain.api.entity.AppointmentStatus
import com.bookk.appointments.domain.api.entity.ClientSnapshot
import com.bookk.appointments.domain.api.entity.EmployeeSnapshot
import com.bookk.appointments.domain.api.entity.PriceAdjustment
import com.bookk.appointments.domain.datasource.AppointmentDataSource
import com.bookk.core.data.DataSource
import com.bookk.core.domain.entity.Error
import com.bookk.core.domain.entity.PaginationMetadata
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.core.neq
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

    override suspend fun getAllPaginated(
        businessId: Uuid,
        limit: Int,
        offset: Long,
        query: String?,
    ): AppointmentPagination = dbQuery {
        if (query.isNullOrBlank()) {
            pageOfAll(businessId, limit, offset)
        } else {
            pageOfMatches(businessId, limit, offset, query.lowercase())
        }
    }

    private suspend fun pageOfAll(businessId: Uuid, limit: Int, offset: Long): AppointmentPagination {
        val appointments = AppointmentEntity
            .find { AppointmentTable.businessId eq businessId }
            .orderBy(AppointmentTable.dateStart to SortOrder.DESC)
        val page = appointments.limit(limit).offset(offset).toList().withDetails().decrypted()
        return pagination(page, total = appointments.count(), limit, offset)
    }

    private suspend fun pageOfMatches(
        businessId: Uuid,
        limit: Int,
        offset: Long,
        query: String,
    ): AppointmentPagination {
        val serviceMatches = (AppointmentServicesTable innerJoin AppointmentTable)
            .select(AppointmentServicesTable.appointmentId)
            .where {
                (AppointmentTable.businessId eq businessId) and
                    (AppointmentServicesTable.serviceName.lowerCase() like "%$query%")
            }
            .map { it[AppointmentServicesTable.appointmentId].value }
            .toSet()
        val appointments = AppointmentEntity
            .find { AppointmentTable.businessId eq businessId }
            .orderBy(AppointmentTable.dateStart to SortOrder.DESC)
            .toList()
        val snapshots = appointments.associateWith { it.storedSnapshot() }
        val reader = snapshotCipher.reader(snapshots.values, AppointmentTable.tableName)
        val matches = appointments.filter {
            it.id.value in serviceMatches || query in reader.client(snapshots.getValue(it)).fullName.lowercase()
        }
        val page = matches.drop(offset.toInt()).take(limit).withDetails().map { it.domain(reader) }
        return pagination(page, total = matches.size.toLong(), limit, offset)
    }

    private fun pagination(page: List<Appointment>, total: Long, limit: Int, offset: Long): AppointmentPagination {
        return AppointmentPagination(
            data = page,
            metadata = PaginationMetadata(
                total = total,
                pageSize = limit,
                page = (offset / limit) + 1,
            ),
        )
    }

    override suspend fun hasOverlapsWith(appointment: AppointmentRepresentation): Boolean = dbQuery {
        AppointmentTable.select(AppointmentTable.id)
            .where {
                (AppointmentTable.businessId eq appointment.businessId)
                    .and(AppointmentTable.initiatorUserId eq appointment.initiatorUserId)
                    .and(AppointmentTable.dateStart.less(appointment.dateEnd))
                    .and(AppointmentTable.dateEnd.greater(appointment.date))
                    .and(AppointmentTable.id neq appointment.id)
                    .and(AppointmentTable.status eq AppointmentStatus.SCHEDULED)
            }
            .limit(1)
            .empty()
            .not()
    }

    override suspend fun create(request: AppointmentRequest): Appointment = dbQuery {
        val encrypted = encrypt(request.client.toClientSnapshot(), request.note, request.employee)
        val appointment = AppointmentEntity.new(request, encrypted)
        request.services.forEach {
            AppointmentServiceEntity.new(appointment.id, it)
        }
        appointment.decrypted()
    }

    override suspend fun create(appointment: Appointment): Appointment = dbQuery {
        val encrypted = encrypt(appointment.client, appointment.note, appointment.employee)
        val appointmentEntity = AppointmentEntity.new(appointment, encrypted)
        appointment.services.forEach {
            AppointmentServiceEntity.new(appointmentEntity.id, it)
        }
        appointment.copy(id = appointmentEntity.id.value)
    }

    override suspend fun update(appointment: Appointment): Appointment = dbQuery {
        val encrypted = encrypt(appointment.client, appointment.note, appointment.employee)
        val appointmentEntity = AppointmentEntity.findByIdAndUpdate(appointment, encrypted) ?: throw Error.NotFound()
        AppointmentServicesTable.deleteWhere {
            AppointmentServicesTable.appointmentId eq appointment.id
        }
        appointment.services.forEach {
            AppointmentServiceEntity.new(appointmentEntity.id, it)
        }
        appointmentEntity.decrypted()
    }

    private suspend fun encrypt(client: ClientSnapshot, note: String, employee: EmployeeSnapshot): EncryptedSnapshot {
        val details = ClientDetails(fullName = client.fullName, phone = client.phone, email = client.email, note = note)
        return snapshotCipher.encrypt(client.id, details, employee.userId, employee.fullName)
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

    override suspend fun anonymizeEmployee(employeeUserId: Uuid) = dbQuery<Unit> {
        AppointmentTable.update(where = { AppointmentTable.employeeUserId eq employeeUserId }) {
            it[employeeName] = null
            it[employeeNameEncrypted] = null
            it[AppointmentTable.employeeUserId] = Uuid.NIL
        }
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
            val snapshots = moved.associateWith { it.storedSnapshot() }
            val reader = snapshotCipher.reader(snapshots.values, AppointmentTable.tableName)
            snapshots.forEach { (entity, snapshot) ->
                val client = snapshotCipher.reencryptClient(snapshot, reader, toClientId)
                entity.clientId = toClientId
                entity.storeClient(client)
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
        this[AppointmentTable.clientName] = null
        this[AppointmentTable.clientPhone] = null
        this[AppointmentTable.clientEmail] = null
        this[AppointmentTable.note] = null
        this[AppointmentTable.clientNameEncrypted] = null
        this[AppointmentTable.clientPhoneEncrypted] = null
        this[AppointmentTable.clientEmailEncrypted] = null
        this[AppointmentTable.noteEncrypted] = null
        this[AppointmentTable.clientUserId] = null
    }

    override suspend fun encryptPlaintextAppointments(limit: Int): Int = dbQuery {
        val plaintext = AppointmentEntity
            .find { AppointmentTable.clientName.isNotNull() or AppointmentTable.employeeName.isNotNull() }
            .limit(limit)
            .forUpdate()
            .toList()
        plaintext.forEach { it.store(snapshotCipher.encryptPlaintext(it.storedSnapshot())) }
        plaintext.size
    }

    private suspend fun AppointmentEntity.decrypted(): Appointment {
        return listOf(this).decrypted().single()
    }

    private suspend fun List<AppointmentEntity>.decrypted(): List<Appointment> {
        val reader = snapshotCipher.reader(map { it.storedSnapshot() }, AppointmentTable.tableName)
        return map { it.domain(reader) }
    }
}
