package com.bookk.business.data.datasource

import com.bookk.business.data.orm.table.ClientTable
import com.bookk.business.data.orm.table.EmployeeInvitationTable
import com.bookk.business.domain.api.employee.entity.EmployeeInvitationStatus
import com.bookk.business.domain.api.export.BusinessClientExport
import com.bookk.business.domain.api.export.PendingInvitationExport
import com.bookk.business.domain.datasource.BusinessDataExportDataSource
import com.bookk.core.data.DataSource
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import kotlin.uuid.Uuid

internal class BusinessDataExportDataSourceImpl : DataSource(), BusinessDataExportDataSource {

    override suspend fun getClients(businessId: Uuid): List<BusinessClientExport> = dbQuery {
        ClientTable.selectAll()
            .where { ClientTable.businessId eq businessId }
            .orderBy(ClientTable.lastName to SortOrder.ASC, ClientTable.name to SortOrder.ASC)
            .map {
                BusinessClientExport(
                    id = it[ClientTable.id].value,
                    name = it[ClientTable.name],
                    lastName = it[ClientTable.lastName],
                    phone = it[ClientTable.phone],
                    email = it[ClientTable.email],
                    description = it[ClientTable.description],
                    hasAccount = it[ClientTable.userId] != null,
                    lastActivityAt = it[ClientTable.lastActivityAt],
                )
            }
    }

    override suspend fun getPendingInvitations(businessId: Uuid): List<PendingInvitationExport> = dbQuery {
        EmployeeInvitationTable.select(
            EmployeeInvitationTable.id,
            EmployeeInvitationTable.invitedBy,
            EmployeeInvitationTable.createdAt,
        )
            .where {
                (EmployeeInvitationTable.businessId eq businessId) and
                    (EmployeeInvitationTable.status eq EmployeeInvitationStatus.PENDING)
            }
            .orderBy(EmployeeInvitationTable.createdAt to SortOrder.ASC)
            .map {
                PendingInvitationExport(
                    id = it[EmployeeInvitationTable.id].value,
                    invitedBy = it[EmployeeInvitationTable.invitedBy],
                    createdAt = it[EmployeeInvitationTable.createdAt],
                )
            }
    }
}
