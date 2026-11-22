package com.bookk.appointments.data.orm.table

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestamp

object SavedBusinessTable : Table("saved_business") {
    val userId = uuid("user_id")
    val businessId = reference("business_id", AppointmentBusinessTable.id, onDelete = ReferenceOption.CASCADE).index()
    val savedAt = timestamp("saved_at")

    override val primaryKey = PrimaryKey(userId, businessId)
}
