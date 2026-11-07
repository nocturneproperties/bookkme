package com.bookk.appointments.data.orm.table

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestamp

object DeletedClientTable : Table("deleted_client") {
    val clientId = uuid("client_id")
    val businessId = uuid("business_id")
    val deletedAt = timestamp("deleted_at")

    override val primaryKey = PrimaryKey(clientId)
}
