package com.bookk.appointments.data.datasource

import com.bookk.appointments.data.orm.table.AppointmentPermissionGrantsTable
import com.bookk.appointments.domain.datasource.AppointmentPermissionDataSource
import com.bookk.core.data.DataSource
import library.permissions.EmployeeAccessSuspended
import library.permissions.ResourcePermission
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.upsert
import kotlin.uuid.Uuid

internal class AppointmentPermissionDataSourceImpl : DataSource(), AppointmentPermissionDataSource {

    override suspend fun setPermission(
        userId: Uuid,
        businessId: Uuid,
        permission: ResourcePermission,
        suspended: Boolean
    ) {
        dbQuery {
            AppointmentPermissionGrantsTable.upsert {
                it[this.userId] = userId
                it[this.businessId] = businessId
                it[this.canView] = permission.view
                it[this.canUpdate] = permission.update
                it[this.canDelete] = permission.delete
                it[this.suspended] = suspended
            }
        }
    }

    override suspend fun getPermission(userId: Uuid, businessId: Uuid): ResourcePermission {
        val grant = dbQuery {
            AppointmentPermissionGrantsTable
                .select(
                    AppointmentPermissionGrantsTable.canView,
                    AppointmentPermissionGrantsTable.canUpdate,
                    AppointmentPermissionGrantsTable.canDelete,
                    AppointmentPermissionGrantsTable.suspended
                )
                .where { (AppointmentPermissionGrantsTable.userId eq userId) and (AppointmentPermissionGrantsTable.businessId eq businessId) }
                .singleOrNull()
        } ?: return ResourcePermission.NONE
        if (grant[AppointmentPermissionGrantsTable.suspended]) throw EmployeeAccessSuspended()
        return ResourcePermission(
            view = grant[AppointmentPermissionGrantsTable.canView],
            update = grant[AppointmentPermissionGrantsTable.canUpdate],
            delete = grant[AppointmentPermissionGrantsTable.canDelete]
        )
    }

    override suspend fun deleteForUser(userId: Uuid) = dbQuery<Unit> {
        AppointmentPermissionGrantsTable.deleteWhere { AppointmentPermissionGrantsTable.userId eq userId }
    }
}
