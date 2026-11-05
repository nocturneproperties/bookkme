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
import com.bookk.appointments.domain.api.entity.AppointmentPagination
import com.bookk.appointments.domain.api.entity.AppointmentRepresentation
import com.bookk.appointments.domain.api.entity.AppointmentRequest
import com.bookk.appointments.domain.api.entity.AppointmentStatus
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
import org.jetbrains.exposed.v1.core.inSubQuery
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

private const val DELETED_CLIENT_NAME = "Deleted Client"
private const val DELETED_USER_NAME = "Deleted User"
private const val DELETED_EMPLOYEE_NAME = "Deleted User"

internal class AppointmentDataSourceImpl : DataSource(), AppointmentDataSource {

    override suspend fun get(id: Uuid): Appointment = dbQuery {
        AppointmentEntity.findById(id)
            ?.domain() ?: throw Error.NotFound()
    }

    override suspend fun getForUpdate(id: Uuid): Appointment = dbQuery {
        AppointmentEntity.find { AppointmentTable.id eq id }
            .forUpdate()
            .singleOrNull()
            ?.domain() ?: throw Error.NotFound()
    }

    override suspend fun getAll(businessId: Uuid): List<Appointment> = dbQuery {
        AppointmentEntity
            .find { AppointmentTable.businessId eq businessId }
            .toList()
            .withDetails()
            .map { it.domain() }
    }

    override suspend fun getAllForDate(businessId: Uuid, range: ClosedRange<Instant>, employeeId: Uuid?): List<Appointment> = dbQuery {
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
            .map { it.domain() }
    }

    override suspend fun getAllPaginated(
        businessId: Uuid,
        limit: Int,
        offset: Long,
        query: String?,
    ): AppointmentPagination = dbQuery {
        var condition = AppointmentTable.businessId.eq(businessId)

        if (!query.isNullOrBlank()) {
            val pattern = "%${query.lowercase()}%"
            val matchingServiceAppointmentIds = AppointmentServicesTable
                .select(AppointmentServicesTable.appointmentId)
                .where { AppointmentServicesTable.serviceName.lowerCase() like pattern }

            condition = condition.and(
                AppointmentTable.clientName.lowerCase().like(pattern)
                    .or(AppointmentTable.id.inSubQuery(matchingServiceAppointmentIds)),
            )
        }

        val appointmentsQuery = AppointmentEntity
            .find { condition }
            .orderBy(AppointmentTable.dateStart to SortOrder.DESC)

        val total = appointmentsQuery.count()
        val result = appointmentsQuery
            .limit(limit)
            .offset(offset)
            .toList()
            .withDetails()
            .map { it.domain() }
        AppointmentPagination(
            data = result,
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
        val appointment = AppointmentEntity.new(request)
        request.services.forEach {
            AppointmentServiceEntity.new(appointment.id, it)
        }
        appointment.domain()
    }

    override suspend fun create(appointment: Appointment): Appointment = dbQuery {
        val appointmentEntity = AppointmentEntity.new(appointment)
        appointment.services.forEach {
            AppointmentServiceEntity.new(appointmentEntity.id, it)
        }
        appointment.copy(id = appointmentEntity.id.value)
    }

    override suspend fun update(appointment: Appointment): Appointment = dbQuery {
        val appointmentEntity = AppointmentEntity.findByIdAndUpdate(appointment) ?: throw Error.NotFound()
        AppointmentServicesTable.deleteWhere {
            AppointmentServicesTable.appointmentId eq appointment.id
        }
        appointment.services.forEach {
            AppointmentServiceEntity.new(appointmentEntity.id, it)
        }
        appointmentEntity.domain()
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
        }?.domain() ?: throw Error.NotFound()
    }

    override suspend fun markCompleted(now: Instant) = dbQuery<Unit> {
        val timeZoneIds = (SettingsTable innerJoin AppointmentBusinessTable)
            .select(AppointmentBusinessTable.timeZone)
            .where { SettingsTable.automaticCompletion eq true }
            .withDistinct()
            .map { it[AppointmentBusinessTable.timeZone] }
        timeZoneIds.forEach { timeZoneId ->
            val timeZone = TimeZone.of(timeZoneId)
            val startOfToday = now.toLocalDateTime(timeZone).date.atStartOfDayIn(timeZone)
            val autoCompletingBusinessIds = (SettingsTable innerJoin AppointmentBusinessTable)
                .select(SettingsTable.businessId)
                .where {
                    (SettingsTable.automaticCompletion eq true)
                        .and(AppointmentBusinessTable.timeZone eq timeZoneId)
                }
            AppointmentTable.update(
                where = {
                    AppointmentTable.status.eq(AppointmentStatus.SCHEDULED)
                        .and(AppointmentTable.dateEnd.less(startOfToday))
                        .and(AppointmentTable.businessId.inSubQuery(autoCompletingBusinessIds))
                },
            ) {
                it[AppointmentTable.status] = AppointmentStatus.COMPLETED
                it[AppointmentTable.completedBy] = AppointmentCompletedBy.SYSTEM
                it[AppointmentTable.updatedAt] = now
            }
        }
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
        }?.domain() ?: throw Error.NotFound()
    }

    override suspend fun adjustPrice(id: Uuid, adjustment: PriceAdjustment): Appointment = dbQuery {
        val appointment = AppointmentEntity.findById(id) ?: throw Error.NotFound()
        appointment.replacePriceAdjustment(adjustment)
        appointment.domain()
    }

    override suspend fun markNoShow(id: Uuid): Appointment = dbQuery {
        AppointmentEntity.findByIdAndUpdate(id) {
            it.status = AppointmentStatus.NO_SHOW
            it.completedBy = null
            it.updatedAt = Clock.System.now()
        }?.domain() ?: throw Error.NotFound()
    }

    override suspend fun anonymizeEmployee(employeeUserId: Uuid) = dbQuery<Unit> {
        AppointmentTable.update(where = { AppointmentTable.employeeUserId eq employeeUserId }) {
            it[employeeName] = DELETED_EMPLOYEE_NAME
            it[AppointmentTable.employeeUserId] = Uuid.NIL
        }
    }

    override suspend fun unlinkInitiator(initiatorUserId: Uuid) = dbQuery<Unit> {
        AppointmentTable.update(where = { AppointmentTable.initiatorUserId eq initiatorUserId }) {
            it[AppointmentTable.initiatorUserId] = Uuid.NIL
        }
    }

    override suspend fun anonymizeClientUser(clientUserId: Uuid) = dbQuery<Unit> {
        AppointmentTable.update(where = { AppointmentTable.clientUserId eq clientUserId }) {
            it.eraseClientDetails(DELETED_USER_NAME)
        }
    }

    override suspend fun cancelUpcomingForUser(
        userId: Uuid,
        startingAfter: Instant,
        asClientReason: String,
        asEmployeeReason: String,
    ): List<Appointment> = dbQuery {
        val upcoming = AppointmentEntity
            .find {
                ((AppointmentTable.clientUserId eq userId) or (AppointmentTable.employeeUserId eq userId))
                    .and(AppointmentTable.status eq AppointmentStatus.SCHEDULED)
                    .and(AppointmentTable.dateStart greater startingAfter)
            }
            .forUpdate()
            .toList()
        val cancelledAt = Clock.System.now()
        upcoming.forEach {
            it.status = AppointmentStatus.CANCELLED
            it.cancellationReason = if (it.clientUserId == userId) asClientReason else asEmployeeReason
            it.updatedAt = cancelledAt
        }
        upcoming.withDetails().map { it.domain() }
    }

    override suspend fun reassignClient(businessId: Uuid, fromClientId: Uuid, toClientId: Uuid) = dbQuery<Unit> {
        AppointmentTable.update(where = { (AppointmentTable.businessId eq businessId) and (AppointmentTable.clientId eq fromClientId) }) {
            it[clientId] = toClientId
        }
    }

    override suspend fun linkClientUser(businessId: Uuid, clientId: Uuid, userId: Uuid) = dbQuery<Unit> {
        AppointmentTable.update(where = { (AppointmentTable.businessId eq businessId) and (AppointmentTable.clientId eq clientId) }) {
            it[clientUserId] = userId
        }
    }

    override suspend fun cancelUpcomingForClient(businessId: Uuid, clientId: Uuid, startingAfter: Instant, reason: String): List<Appointment> = dbQuery {
        val upcoming = AppointmentEntity
            .find {
                (AppointmentTable.businessId eq businessId)
                    .and(AppointmentTable.clientId eq clientId)
                    .and(AppointmentTable.status eq AppointmentStatus.SCHEDULED)
                    .and(AppointmentTable.dateStart greater startingAfter)
            }
            .forUpdate()
            .toList()
        val cancelledAt = Clock.System.now()
        upcoming.forEach {
            it.status = AppointmentStatus.CANCELLED
            it.cancellationReason = reason
            it.updatedAt = cancelledAt
        }
        upcoming.withDetails().map { it.domain() }
    }

    override suspend fun anonymizeClient(businessId: Uuid, clientId: Uuid) = dbQuery<Unit> {
        AppointmentTable.update(where = { (AppointmentTable.businessId eq businessId) and (AppointmentTable.clientId eq clientId) }) {
            it.eraseClientDetails(DELETED_CLIENT_NAME)
        }
    }

    private fun UpdateStatement.eraseClientDetails(placeholderName: String) {
        this[AppointmentTable.clientName] = placeholderName
        this[AppointmentTable.clientPhone] = null
        this[AppointmentTable.clientEmail] = null
        this[AppointmentTable.clientUserId] = null
        this[AppointmentTable.note] = ""
    }
}
