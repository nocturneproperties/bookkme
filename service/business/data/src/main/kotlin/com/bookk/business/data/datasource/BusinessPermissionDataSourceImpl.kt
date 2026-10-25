package com.bookk.business.data.datasource

import com.bookk.business.data.orm.table.BusinessPermissionGrantsTable
import com.bookk.business.data.orm.table.ClientTable
import com.bookk.business.data.orm.table.EmployeeTable
import com.bookk.business.domain.api.business.entity.BusinessResource
import com.bookk.business.domain.api.business.entity.BusinessViewer
import com.bookk.business.domain.api.employee.entity.Employee
import com.bookk.business.domain.datasource.BusinessPermissionDataSource
import com.bookk.core.data.DataSource
import library.permissions.EmployeeAccessSuspended
import library.permissions.ResourcePermission
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import kotlin.uuid.Uuid

internal class BusinessPermissionDataSourceImpl : DataSource(), BusinessPermissionDataSource {

    override suspend fun getViewer(userId: Uuid, businessId: Uuid): BusinessViewer =
        getViewers(userId, listOf(businessId)).getValue(businessId)

    override suspend fun getViewers(userId: Uuid, businessIds: List<Uuid>): Map<Uuid, BusinessViewer> {
        if (businessIds.isEmpty()) return emptyMap()
        return dbQuery {
            val activeEmployeeIds = EmployeeTable
                .select(EmployeeTable.businessId, EmployeeTable.id)
                .where {
                    (EmployeeTable.userId eq userId) and
                        (EmployeeTable.businessId inList businessIds) and
                        EmployeeTable.suspendedAt.isNull()
                }
                .associate { it[EmployeeTable.businessId].value to it[EmployeeTable.id].value }
            val grantsByBusinessId = BusinessPermissionGrantsTable
                .selectAll()
                .where { BusinessPermissionGrantsTable.employeeId inList activeEmployeeIds.values }
                .groupBy { it[BusinessPermissionGrantsTable.businessId].value }
            val clientIds = ClientTable
                .select(ClientTable.businessId, ClientTable.id)
                .where { (ClientTable.userId eq userId) and (ClientTable.businessId inList businessIds) }
                .associate { it[ClientTable.businessId].value to it[ClientTable.id].value }
            businessIds.associateWith { businessId ->
                BusinessViewer(
                    permissions = BusinessPermissionGrantsTable.permissionsOf(grantsByBusinessId[businessId].orEmpty()),
                    employeeId = activeEmployeeIds[businessId],
                    clientId = clientIds[businessId]
                )
            }
        }
    }

    override suspend fun getPermission(userId: Uuid, businessId: Uuid, resource: BusinessResource): ResourcePermission {
        val grant = dbQuery {
            BusinessPermissionGrantsTable
                .innerJoin(EmployeeTable)
                .select(
                    BusinessPermissionGrantsTable.canView,
                    BusinessPermissionGrantsTable.canUpdate,
                    BusinessPermissionGrantsTable.canDelete,
                    EmployeeTable.suspendedAt
                )
                .where {
                    (BusinessPermissionGrantsTable.userId eq userId) and
                        (BusinessPermissionGrantsTable.businessId eq businessId) and
                        (BusinessPermissionGrantsTable.resource eq resource)
                }
                .singleOrNull()
        } ?: return ResourcePermission.NONE
        if (grant[EmployeeTable.suspendedAt] != null) throw EmployeeAccessSuspended()
        return ResourcePermission(
            view = grant[BusinessPermissionGrantsTable.canView],
            update = grant[BusinessPermissionGrantsTable.canUpdate],
            delete = grant[BusinessPermissionGrantsTable.canDelete]
        )
    }

    override suspend fun setPermissions(employee: Employee, grants: Map<BusinessResource, ResourcePermission>) = dbQuery {
        BusinessPermissionGrantsTable.upsertGrants(employee.id, employee.userId, employee.businessId, grants)
    }

    override suspend fun deleteUserPermissions(userId: Uuid) = dbQuery<Unit> {
        BusinessPermissionGrantsTable.deleteWhere { BusinessPermissionGrantsTable.userId eq userId }
    }
}
