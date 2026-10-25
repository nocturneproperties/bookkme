package com.bookk.business.data.datasource

import com.bookk.business.data.orm.entity.EmployeeInvitationEntity
import com.bookk.business.data.orm.table.EmployeeInvitationTable
import com.bookk.business.domain.api.employee.entity.EmployeeInvitation
import com.bookk.business.domain.api.employee.entity.EmployeeInvitationStatus
import com.bookk.business.domain.datasource.EmployeeInvitationDataSource
import com.bookk.core.data.DataSource
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

internal class EmployeeInvitationDataSourceImpl : DataSource(), EmployeeInvitationDataSource {

    override suspend fun createInvitation(invitation: EmployeeInvitation): EmployeeInvitation = dbQuery {
        val id = EmployeeInvitationTable.insertAndGetId {
            it[businessId] = invitation.businessId
            it[invitedBy] = invitation.invitedBy
            it[codeHash] = invitation.code
            it[status] = EmployeeInvitationStatus.PENDING
        }
        invitation.copy(id = id.value, status = EmployeeInvitationStatus.PENDING)
    }

    override suspend fun getInvitation(businessId: Uuid, id: Uuid): EmployeeInvitation? = dbQuery {
        EmployeeInvitationEntity.find {
            (EmployeeInvitationTable.businessId eq businessId) and
                (EmployeeInvitationTable.id eq id)
        }
            .firstOrNull()
            ?.toDomain()
    }

    override suspend fun getInvitationByCodeHash(codeHash: String): EmployeeInvitation? = dbQuery {
        EmployeeInvitationEntity.find { EmployeeInvitationTable.codeHash eq codeHash }
            .firstOrNull()
            ?.toDomain()
    }

    override suspend fun countPendingInvitations(businessId: Uuid): Long = dbQuery {
        EmployeeInvitationEntity.count(
            (EmployeeInvitationTable.businessId eq businessId) and
                (EmployeeInvitationTable.status eq EmployeeInvitationStatus.PENDING)
        )
    }

    override suspend fun countInvitationsCreatedSince(businessId: Uuid, since: Instant): Long = dbQuery {
        EmployeeInvitationEntity.count(
            (EmployeeInvitationTable.businessId eq businessId) and
                (EmployeeInvitationTable.createdAt greaterEq since)
        )
    }

    override suspend fun getInvitationsByInviter(businessId: Uuid, invitedBy: Uuid): List<EmployeeInvitation> =
        dbQuery {
            EmployeeInvitationEntity.find {
                (EmployeeInvitationTable.businessId eq businessId) and
                    (EmployeeInvitationTable.invitedBy eq invitedBy)
            }
                .toList()
                .map(EmployeeInvitationEntity::toDomain)
        }

    override suspend fun redeemInvitation(id: Uuid): Boolean = dbQuery {
        EmployeeInvitationTable.update(
            where = {
                (EmployeeInvitationTable.id eq id) and
                    (EmployeeInvitationTable.status eq EmployeeInvitationStatus.PENDING)
            }
        ) {
            it[status] = EmployeeInvitationStatus.REDEEMED
            it[codeHash] = null
            it[updatedAt] = Clock.System.now()
        } != 0
    }

    override suspend fun revokeInvitation(id: Uuid): Boolean = dbQuery {
        EmployeeInvitationTable.update(
            where = {
                (EmployeeInvitationTable.id eq id) and
                    (EmployeeInvitationTable.status eq EmployeeInvitationStatus.PENDING)
            }
        ) {
            it[status] = EmployeeInvitationStatus.REVOKED
            it[codeHash] = null
            it[updatedAt] = Clock.System.now()
        } != 0
    }

    override suspend fun expireOldInvitations(before: Instant) {
        dbQuery {
            EmployeeInvitationTable.update(
                where = {
                    (EmployeeInvitationTable.status eq EmployeeInvitationStatus.PENDING) and
                        (EmployeeInvitationTable.createdAt less before)
                }
            ) {
                it[status] = EmployeeInvitationStatus.EXPIRED
                it[codeHash] = null
                it[updatedAt] = Clock.System.now()
            }
        }
    }

    override suspend fun deleteProcessedInvitations(before: Instant) {
        dbQuery {
            EmployeeInvitationTable.deleteWhere {
                val processed = EmployeeInvitationTable.status neq EmployeeInvitationStatus.PENDING
                val updatedBeforeCutoff = EmployeeInvitationTable.updatedAt less before
                val neverUpdatedAndCreatedBeforeCutoff = EmployeeInvitationTable.updatedAt.isNull() and
                    (EmployeeInvitationTable.createdAt less before)
                processed and (updatedBeforeCutoff or neverUpdatedAndCreatedBeforeCutoff)
            }
        }
    }
}
