package com.bookk.business.data.datasource

import com.bookk.business.data.orm.entity.BusinessEntity
import com.bookk.business.data.orm.entity.withSchedule
import com.bookk.business.data.orm.table.BusinessDashboardTable
import com.bookk.business.data.orm.table.BusinessDayOffTable
import com.bookk.business.data.orm.table.BusinessTable
import com.bookk.business.data.orm.table.EmployeeTable
import com.bookk.business.domain.api.business.entity.Business
import com.bookk.business.domain.api.business.entity.BusinessLimits
import com.bookk.business.domain.api.business.entity.BusinessUpdateModel
import com.bookk.business.domain.api.business.entity.UserBusinesses
import com.bookk.business.domain.datasource.BusinessDataSource
import com.bookk.business.domain.datasource.LockedBusiness
import com.bookk.business.domain.datasource.RowLock
import com.bookk.core.data.DataSource
import com.bookk.core.data.forShare
import com.bookk.core.domain.entity.Error
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNotNull
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class BusinessDataSourceImpl : DataSource(), BusinessDataSource {
    override suspend fun createBusiness(
        userId: Uuid,
        name: String,
        currencyCode: String,
        timeZone: TimeZone,
        demo: Boolean,
    ): Business = dbQuery {
        val entity = BusinessEntity.new(userId, name, currencyCode, timeZone, demo)
        BusinessDashboardTable.upsert {
            it[this.userId] = userId
            it[businessId] = entity.id
        }
        entity.toDomain()
    }

    override suspend fun endDemoOfBusinessesOwnedBy(userId: Uuid) = dbQuery<Unit> {
        BusinessTable.update(where = { (BusinessTable.userId eq userId) and (BusinessTable.demo eq true) }) {
            it[demo] = false
            it[updatedAt] = Clock.System.now()
        }
    }

    override suspend fun updateBusiness(model: BusinessUpdateModel, updatedAt: Instant) = dbQuery<Business> {
        val entity = BusinessEntity.findByIdAndUpdate(model, updatedAt) ?: throw Error.NotFound()
        entity.toDomain()
    }

    override suspend fun getBusinessById(id: Uuid): Business? = dbQuery {
        BusinessEntity.findById(id)?.toDomain()
    }

    override suspend fun getBusinessesByIds(ids: Collection<Uuid>): List<Business> = dbQuery {
        BusinessEntity
            .find { BusinessTable.id inList ids }
            .withSchedule()
            .map { it.toDomain() }
    }

    override suspend fun lockBusiness(id: Uuid, lock: RowLock): LockedBusiness? = dbQuery {
        val query = BusinessTable.select(
            BusinessTable.currency,
            BusinessTable.maxEmployees,
            BusinessTable.maxClients,
            BusinessTable.maxServices,
            BusinessTable.maxServiceGroups,
        ).where { BusinessTable.id eq id }
        val lockedQuery = when (lock) {
            RowLock.SHARED -> query.forShare()
            RowLock.EXCLUSIVE -> query.forUpdate()
        }
        lockedQuery.singleOrNull()?.let {
            LockedBusiness(currencyCode = it[BusinessTable.currency], limits = it.limits())
        }
    }

    override suspend fun countBusinessesOwnedBy(userId: Uuid): Long = dbQuery {
        BusinessTable.select(BusinessTable.id).where { BusinessTable.userId eq userId }.count()
    }

    override suspend fun isOwner(userId: Uuid, businessId: Uuid): Boolean = dbQuery {
        BusinessTable.select(BusinessTable.id)
            .where { (BusinessTable.id eq businessId) and (BusinessTable.userId eq userId) }
            .empty()
            .not()
    }

    override suspend fun getUserBusinessIdsForUpdate(userId: Uuid): List<Uuid> = dbQuery {
        BusinessTable.select(BusinessTable.id)
            .where { BusinessTable.userId eq userId }
            .forUpdate()
            .map { it[BusinessTable.id].value }
    }

    override suspend fun deleteBusinesses(businessIds: Collection<Uuid>) {
        if (businessIds.isEmpty()) return
        dbQuery { BusinessTable.deleteWhere { BusinessTable.id inList businessIds } }
    }

    override suspend fun getDashboardBusiness(userId: Uuid): Business? = dbQuery {
        val businessId = BusinessDashboardTable
            .select(BusinessDashboardTable.businessId)
            .where { BusinessDashboardTable.userId eq userId }
            .singleOrNull()
            ?.get(BusinessDashboardTable.businessId)
            ?.value
        businessId
            ?.takeUnless { isSuspendedEmployee(userId, it) }
            ?.let { BusinessEntity.findById(it)?.toDomain() }
    }

    private fun isSuspendedEmployee(userId: Uuid, businessId: Uuid): Boolean = EmployeeTable.select(EmployeeTable.id)
        .where {
            (EmployeeTable.userId eq userId) and
                (EmployeeTable.businessId eq businessId) and
                EmployeeTable.suspendedAt.isNotNull()
        }
        .empty()
        .not()

    override suspend fun setDashboardBusiness(userId: Uuid, businessId: Uuid): Unit = dbQuery {
        BusinessDashboardTable.upsert {
            it[this.userId] = userId
            it[this.businessId] = businessId
            it[this.updatedAt] = Clock.System.now()
        }
    }

    override suspend fun getUserBusinesses(userId: Uuid): UserBusinesses = dbQuery {
        val dashboardId = BusinessDashboardTable
            .select(BusinessDashboardTable.businessId)
            .where { BusinessDashboardTable.userId eq userId }
            .singleOrNull()
            ?.getOrNull(BusinessDashboardTable.businessId)
            ?.value
        val activeEmployeeBusinessIds = EmployeeTable
            .select(EmployeeTable.businessId)
            .where { (EmployeeTable.userId eq userId) and EmployeeTable.suspendedAt.isNull() }
            .map { it[EmployeeTable.businessId].value }
        val businesses = BusinessEntity
            .find { (BusinessTable.userId eq userId) or (BusinessTable.id inList activeEmployeeBusinessIds) }
            .withSchedule()
            .map { it.toDomain() }
        UserBusinesses(
            dashboardId = dashboardId?.takeIf { id -> businesses.any { it.id == id } },
            businesses = businesses,
        )
    }

    override suspend fun deleteDayOffsInThePast() = dbQuery {
        val now = Clock.System.now()
        val businessIdsByTimeZone = BusinessTable
            .select(BusinessTable.id, BusinessTable.timezone)
            .groupBy(
                keySelector = { it[BusinessTable.timezone] },
                valueTransform = { it[BusinessTable.id].value },
            )

        businessIdsByTimeZone.forEach { (timeZone, businessIds) ->
            val today = now.toLocalDateTime(TimeZone.of(timeZone)).date
            BusinessDayOffTable.deleteWhere {
                BusinessDayOffTable.businessId.inList(businessIds)
                    .and(BusinessDayOffTable.endDate.less(today))
            }
        }
    }

    override suspend fun getClientDataRetentionMonths(): Map<Uuid, Int> = dbQuery {
        BusinessTable
            .select(BusinessTable.id, BusinessTable.clientDataRetentionMonths)
            .associate { it[BusinessTable.id].value to it[BusinessTable.clientDataRetentionMonths] }
    }

    private fun ResultRow.limits(): BusinessLimits {
        return BusinessLimits(
            maxEmployees = this[BusinessTable.maxEmployees] ?: BusinessLimits.DEFAULT_MAX_EMPLOYEES,
            maxClients = this[BusinessTable.maxClients] ?: BusinessLimits.DEFAULT_MAX_CLIENTS,
            maxServices = this[BusinessTable.maxServices] ?: BusinessLimits.DEFAULT_MAX_SERVICES,
            maxServiceGroups = this[BusinessTable.maxServiceGroups] ?: BusinessLimits.DEFAULT_MAX_SERVICE_GROUPS,
        )
    }
}
