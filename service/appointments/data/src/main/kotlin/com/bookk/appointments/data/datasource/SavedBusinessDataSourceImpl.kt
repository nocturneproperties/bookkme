package com.bookk.appointments.data.datasource

import com.bookk.appointments.data.orm.table.AppointmentBusinessTable
import com.bookk.appointments.data.orm.table.AppointmentTable
import com.bookk.appointments.data.orm.table.SavedBusinessTable
import com.bookk.appointments.domain.api.entity.AppointmentStatus
import com.bookk.appointments.domain.api.entity.SavedBusiness
import com.bookk.appointments.domain.datasource.SavedBusinessDataSource
import com.bookk.core.data.DataSource
import org.jetbrains.exposed.v1.core.JoinType
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.max
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class SavedBusinessDataSourceImpl : DataSource(), SavedBusinessDataSource {

    override suspend fun isSaved(userId: Uuid, businessId: Uuid): Boolean = dbQuery {
        SavedBusinessTable
            .select(SavedBusinessTable.businessId)
            .where { (SavedBusinessTable.userId eq userId) and (SavedBusinessTable.businessId eq businessId) }
            .empty()
            .not()
    }

    override suspend fun count(userId: Uuid): Long = dbQuery {
        SavedBusinessTable.selectAll().where { SavedBusinessTable.userId eq userId }.count()
    }

    override suspend fun save(userId: Uuid, businessId: Uuid, savedAt: Instant) = dbQuery<Unit> {
        SavedBusinessTable.insertIgnore {
            it[this.userId] = userId
            it[this.businessId] = businessId
            it[this.savedAt] = savedAt
        }
    }

    override suspend fun remove(userId: Uuid, businessId: Uuid) = dbQuery<Unit> {
        SavedBusinessTable.deleteWhere {
            (SavedBusinessTable.userId eq userId) and (SavedBusinessTable.businessId eq businessId)
        }
    }

    override suspend fun getAll(userId: Uuid): List<SavedBusiness> = dbQuery {
        val rows = SavedBusinessTable
            .join(AppointmentBusinessTable, JoinType.INNER, SavedBusinessTable.businessId, AppointmentBusinessTable.id)
            .select(SavedBusinessTable.businessId, SavedBusinessTable.savedAt, AppointmentBusinessTable.enabled)
            .where { SavedBusinessTable.userId eq userId }
            .orderBy(SavedBusinessTable.savedAt to SortOrder.DESC)
            .toList()
        val lastVisits = lastVisits(userId, rows.map { it[SavedBusinessTable.businessId].value })
        rows.map { it.toSavedBusiness(lastVisits) }
    }

    override suspend fun getSavedAt(userId: Uuid): Map<Uuid, Instant> = dbQuery {
        SavedBusinessTable
            .select(SavedBusinessTable.businessId, SavedBusinessTable.savedAt)
            .where { SavedBusinessTable.userId eq userId }
            .associate { it[SavedBusinessTable.businessId].value to it[SavedBusinessTable.savedAt] }
    }

    override suspend fun deleteForUser(userId: Uuid) = dbQuery<Unit> {
        SavedBusinessTable.deleteWhere { SavedBusinessTable.userId eq userId }
    }

    private fun lastVisits(userId: Uuid, businessIds: List<Uuid>): Map<Uuid, Instant?> {
        if (businessIds.isEmpty()) return emptyMap()
        val lastStart = AppointmentTable.dateStart.max()
        return AppointmentTable
            .select(AppointmentTable.businessId, lastStart)
            .where {
                (AppointmentTable.clientUserId eq userId)
                    .and(AppointmentTable.status eq AppointmentStatus.COMPLETED)
                    .and(AppointmentTable.businessId inList businessIds)
            }
            .groupBy(AppointmentTable.businessId)
            .associate { it[AppointmentTable.businessId].value to it[lastStart] }
    }

    private fun ResultRow.toSavedBusiness(lastVisits: Map<Uuid, Instant?>): SavedBusiness {
        val businessId = this[SavedBusinessTable.businessId].value
        return SavedBusiness(
            businessId = businessId,
            enabled = this[AppointmentBusinessTable.enabled],
            savedAt = this[SavedBusinessTable.savedAt],
            lastVisitAt = lastVisits[businessId],
        )
    }
}
