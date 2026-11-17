package com.bookk.business.data.orm.table

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestamp
import kotlin.time.Clock

object UserLimitsTable : Table("user_limits") {
    val userId = uuid("user_id")
    val maxBusinesses = integer("max_businesses").nullable()
    val createdAt = timestamp("created_at").clientDefault { Clock.System.now() }

    override val primaryKey = PrimaryKey(userId)
}
