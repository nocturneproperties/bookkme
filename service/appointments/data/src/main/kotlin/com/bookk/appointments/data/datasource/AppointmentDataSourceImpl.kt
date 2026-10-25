package com.bookk.appointments.data.datasource

import com.bookk.appointments.data.orm.entity.AppointmentEntity
import com.bookk.appointments.data.orm.entity.AppointmentServiceEntity
import com.bookk.appointments.data.orm.entity.withDetails
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
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

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

    override suspend fun getAllForDate(businessId: Uuid, range: ClosedRange<Instant>, employeeId: Uuid?): List<Appointment> {
        return dbQuery {
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
    }

    override suspend fun getAllPaginated(
        businessId: Uuid,
        limit: Int,
        offset: Long,
        query: String?
    ): AppointmentPagination {
        return dbQuery {
            var condition = AppointmentTable.businessId.eq(businessId)

            if (!query.isNullOrBlank()) {
                val pattern = "%${query.lowercase()}%"
                val matchingServiceAppointmentIds = AppointmentServicesTable
                    .select(AppointmentServicesTable.appointmentId)
                    .where { AppointmentServicesTable.serviceName.lowerCase() like pattern }

                condition = condition.and(
                    AppointmentTable.clientName.lowerCase().like(pattern)
                        .or(AppointmentTable.id.inSubQuery(matchingServiceAppointmentIds))
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
                    page = (offset / limit) + 1
                ),
            )
        }
    }

    override suspend fun hasOverlapsWith(appointment: AppointmentRepresentation): Boolean {
        return dbQuery {
            AppointmentTable.select(AppointmentTable.id)
                .where {
                    (AppointmentTable.businessId eq appointment.businessId)
                        .and(AppointmentTable.userId eq appointment.userId)
                        .and(AppointmentTable.dateStart.less(appointment.dateEnd))
                        .and(AppointmentTable.dateEnd.greater(appointment.date))
                        .and(AppointmentTable.id neq appointment.id)
                        .and(AppointmentTable.status eq AppointmentStatus.SCHEDULED)
                }
                .limit(1)
                .empty()
                .not()
        }
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
            it.cancellationReason = reason
            it.updatedAt = Clock.System.now()
        }?.domain() ?: throw Error.NotFound()
    }

    override suspend fun markCompleted(before: Instant) = dbQuery<Unit> {
        val autoCompletingBusinessIds = SettingsTable
            .select(SettingsTable.businessId)
            .where { SettingsTable.automaticCompletion eq true }
        AppointmentTable.update(
            where = {
                AppointmentTable.status.eq(AppointmentStatus.SCHEDULED)
                    .and(AppointmentTable.dateEnd.less(before))
                    .and(AppointmentTable.businessId.inSubQuery(autoCompletingBusinessIds))
            }
        ) {
            it[AppointmentTable.status] = AppointmentStatus.COMPLETED
            it[AppointmentTable.completedBy] = AppointmentCompletedBy.SYSTEM
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

    override suspend fun anonymizeForUser(userId: Uuid) = dbQuery<Unit> {
        AppointmentTable.update(where = { AppointmentTable.clientId eq userId }) {
            it[clientName] = "Deleted User"
            it[clientPhone] = null
            it[clientEmail] = null
        }
        AppointmentTable.update(where = { AppointmentTable.employeeUserId eq userId }) {
            it[employeeName] = "Deleted User"
        }
    }
}