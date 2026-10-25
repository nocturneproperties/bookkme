package com.bookk.appointments.data.datasource

import com.bookk.appointments.data.orm.entity.AppointmentRequestEntity
import com.bookk.appointments.data.orm.entity.AppointmentRequestServiceEntity
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
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class AppointmentRequestDataSourceImpl(
    private val cacheClient: CacheClient<String>
) : DataSource(), AppointmentRequestDataSource {
    override suspend fun get(id: Uuid): AppointmentRequest? = dbQuery {
        AppointmentRequestEntity.findById(id)
            ?.domain()
    }

    override suspend fun getForUpdate(id: Uuid): AppointmentRequest? = dbQuery {
        AppointmentRequestEntity.find { AppointmentRequestTable.id eq id }
            .forUpdate()
            .singleOrNull()
            ?.domain()
    }

    override suspend fun getPending(businessId: Uuid): List<AppointmentRequest> = dbQuery {
        AppointmentRequestEntity
            .find {
                AppointmentRequestTable.businessId.eq(businessId)
                    .and(AppointmentRequestTable.status.eq(AppointmentRequestStatus.PENDING))
            }
            .map { it.domain() }
    }

    override suspend fun getPendingByUser(userId: Uuid, businessId: Uuid): List<AppointmentRequest> = dbQuery {
        AppointmentRequestEntity
            .find {
                AppointmentRequestTable.userId.eq(userId)
                    .and(AppointmentRequestTable.businessId.eq(businessId))
                    .and(AppointmentRequestTable.status.eq(AppointmentRequestStatus.PENDING))
            }
            .map { it.domain() }
    }

    override suspend fun create(request: AppointmentRequest): AppointmentRequest = dbQuery {
        val requestEntity = AppointmentRequestEntity.new(request)
        request.services.forEach {
            AppointmentRequestServiceEntity.new(requestEntity.id, it)
        }
        request.copy(id = requestEntity.id.value)
    }

    override suspend fun update(request: AppointmentRequest): AppointmentRequest = dbQuery {
        val requestEntity = AppointmentRequestEntity.findByIdAndUpdate(request) ?: throw Error.NotFound()
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
        }?.domain() ?: throw Error.NotFound()
    }

    override suspend fun hasOverlapsWith(request: AppointmentRequest): Boolean {
        return dbQuery {
            AppointmentRequestTable.select(AppointmentRequestTable.id)
                .where {
                    (AppointmentRequestTable.businessId eq request.businessId)
                        .and(AppointmentRequestTable.userId eq request.userId)
                        .and(AppointmentRequestTable.dateStart.less(request.dateEnd))
                        .and(AppointmentRequestTable.dateEnd.greater(request.date))
                        .and(AppointmentRequestTable.status neq AppointmentRequestStatus.DECLINED)
                }
                .limit(1)
                .empty()
                .not()
        }
    }

    override suspend fun cancelOutdated(before: Instant) = dbQuery<Unit> {
        AppointmentRequestTable.update(
            where = {
                AppointmentRequestTable.status.eq(AppointmentRequestStatus.PENDING)
                    .and(AppointmentRequestTable.dateEnd.less(before))
            }
        ) {
            it[AppointmentRequestTable.status] = AppointmentRequestStatus.CANCELLED
            it[AppointmentRequestTable.updatedAt] = Clock.System.now()
        }
    }

    override suspend fun deleteForUser(userId: Uuid) = dbQuery<Unit> {
        AppointmentRequestTable.deleteWhere {
            (AppointmentRequestTable.clientId eq userId) or (AppointmentRequestTable.employeeUserId eq userId)
        }
    }

    override suspend fun reserveOfferToken(token: String): Boolean = mapExceptions {
        cacheClient.setIfAbsent(token, "marker", 10.minutes)
    }

    override suspend fun releaseOfferToken(token: String) = mapExceptions {
        cacheClient.delete(token)
    }
}