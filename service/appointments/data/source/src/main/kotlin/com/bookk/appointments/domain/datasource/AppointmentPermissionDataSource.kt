package com.bookk.appointments.domain.datasource

import library.permissions.ResourcePermission
import kotlin.uuid.Uuid

interface AppointmentPermissionDataSource {
    suspend fun setPermission(userId: Uuid, businessId: Uuid, permission: ResourcePermission, suspended: Boolean)
    suspend fun getPermission(userId: Uuid, businessId: Uuid): ResourcePermission
    suspend fun deleteForUser(userId: Uuid)
}
