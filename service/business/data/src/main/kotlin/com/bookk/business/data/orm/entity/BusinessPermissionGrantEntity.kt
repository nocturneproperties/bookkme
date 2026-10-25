package com.bookk.business.data.orm.entity

import com.bookk.business.data.orm.table.BusinessPermissionGrantsTable
import library.permissions.ResourcePermission
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.dao.UuidEntity
import org.jetbrains.exposed.v1.dao.UuidEntityClass
import kotlin.uuid.Uuid

internal class BusinessPermissionGrantEntity(id: EntityID<Uuid>) : UuidEntity(id) {
    val resource by BusinessPermissionGrantsTable.resource
    val canView by BusinessPermissionGrantsTable.canView
    val canUpdate by BusinessPermissionGrantsTable.canUpdate
    val canDelete by BusinessPermissionGrantsTable.canDelete

    fun permission() = ResourcePermission(view = canView, update = canUpdate, delete = canDelete)

    companion object : UuidEntityClass<BusinessPermissionGrantEntity>(BusinessPermissionGrantsTable)
}
