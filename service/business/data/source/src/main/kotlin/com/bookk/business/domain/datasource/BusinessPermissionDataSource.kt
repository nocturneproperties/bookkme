package com.bookk.business.domain.datasource

import com.bookk.business.domain.api.business.entity.BusinessResource
import com.bookk.business.domain.api.business.entity.BusinessViewer
import com.bookk.business.domain.api.employee.entity.Employee
import library.permissions.ResourcePermission
import kotlin.uuid.Uuid

interface BusinessPermissionDataSource {
    suspend fun getViewer(userId: Uuid, businessId: Uuid): BusinessViewer
    suspend fun getViewers(userId: Uuid, businessIds: List<Uuid>): Map<Uuid, BusinessViewer>
    suspend fun getPermission(userId: Uuid, businessId: Uuid, resource: BusinessResource): ResourcePermission
    suspend fun setPermissions(employee: Employee, grants: Map<BusinessResource, ResourcePermission>)
    suspend fun deleteUserPermissions(userId: Uuid)
}
