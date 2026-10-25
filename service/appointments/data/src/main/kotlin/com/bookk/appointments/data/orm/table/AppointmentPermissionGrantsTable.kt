package com.bookk.appointments.data.orm.table

import com.bookk.core.data.database.BaseUUIDTable
import org.jetbrains.exposed.v1.core.ReferenceOption

object AppointmentPermissionGrantsTable : BaseUUIDTable("appointment_permission_grants") {
    val userId = uuid("userId")
    val businessId = reference("business_id", AppointmentBusinessTable.id, onDelete = ReferenceOption.CASCADE)
    val canView = bool("can_view")
    val canUpdate = bool("can_update")
    val canDelete = bool("can_delete")
    val suspended = bool("suspended").default(false)

    init {
        index(true, userId, businessId)
    }
}
