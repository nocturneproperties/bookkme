package com.bookk.business.data.orm.table

import com.bookk.business.domain.api.business.entity.BusinessPermissions
import com.bookk.business.domain.api.business.entity.BusinessResource
import library.permissions.ResourcePermission
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.dao.id.UuidTable
import org.jetbrains.exposed.v1.jdbc.batchUpsert
import kotlin.uuid.Uuid

object BusinessPermissionGrantsTable : UuidTable("business_permission_grants") {
    val userId = uuid("user_id")
    val businessId = reference("business_id", BusinessTable, onDelete = ReferenceOption.CASCADE)
    val employeeId = reference("employee_id", EmployeeTable, onDelete = ReferenceOption.CASCADE).index()
    val resource = enumeration("resource", BusinessResource::class)
    val canView = bool("can_view")
    val canUpdate = bool("can_update")
    val canDelete = bool("can_delete")

    init {
        index(isUnique = true, userId, businessId, resource)
    }

    fun upsertGrants(employeeId: Uuid, userId: Uuid, businessId: Uuid, grants: Map<BusinessResource, ResourcePermission>) {
        batchUpsert(grants.entries) { (grantResource, permission) ->
            this[BusinessPermissionGrantsTable.employeeId] = employeeId
            this[BusinessPermissionGrantsTable.userId] = userId
            this[BusinessPermissionGrantsTable.businessId] = businessId
            this[resource] = grantResource
            this[canView] = permission.view
            this[canUpdate] = permission.update
            this[canDelete] = permission.delete
        }
    }

    fun permissionsOf(rows: Iterable<ResultRow>): BusinessPermissions = BusinessPermissions.from(
        rows.associate { it[resource] to ResourcePermission(view = it[canView], update = it[canUpdate], delete = it[canDelete]) }
    )
}
