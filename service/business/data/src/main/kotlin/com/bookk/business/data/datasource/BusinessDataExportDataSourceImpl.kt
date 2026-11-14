package com.bookk.business.data.datasource

import com.bookk.business.data.orm.entity.ClientEntity
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
import kotlin.uuid.Uuid

internal class BusinessDataExportDataSourceImpl(
    private val contactCipher: ContactCipher,
) : DataSource(), BusinessDataExportDataSource {

    override suspend fun getClients(businessId: Uuid): List<BusinessClientExport> = dbQuery {
        val clients = ClientEntity.find { ClientTable.businessId eq businessId }.toList()
        val reader = contactCipher.reader(clients.map { it.id.value }, CLIENT_SOURCE)
        clients
            .map { entity ->
                val contact = entity.contact(reader)
                BusinessClientExport(
                    id = entity.id.value,
                    name = contact.name,
                    lastName = contact.lastName,
                    phone = contact.phone,
                    email = contact.email,
                    description = contact.description,
                    hasAccount = entity.userId != null,
                    lastActivityAt = entity.lastActivityAt,
                )
            }
            .sortedWith(BY_LAST_NAME_THEN_NAME)
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

    private companion object {
        const val CLIENT_SOURCE = "client"
        val BY_LAST_NAME_THEN_NAME: Comparator<BusinessClientExport> =
            compareBy<BusinessClientExport, String>(String.CASE_INSENSITIVE_ORDER) { it.lastName }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
    }
}
