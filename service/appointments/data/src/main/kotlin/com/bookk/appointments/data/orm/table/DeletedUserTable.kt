package com.bookk.appointments.data.orm.table

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestamp

object DeletedUserTable : Table("deleted_user") {
    val userId = uuid("user_id")
    val deletedAt = timestamp("deleted_at")

    override val primaryKey = PrimaryKey(userId)
}
