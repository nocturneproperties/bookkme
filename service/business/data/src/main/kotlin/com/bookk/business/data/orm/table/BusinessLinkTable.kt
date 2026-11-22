package com.bookk.business.data.orm.table

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.UuidTable
import org.jetbrains.exposed.v1.datetime.timestamp
import kotlin.time.Clock

object BusinessLinkTable : UuidTable("business_link") {
    val businessId = reference("business_id", BusinessTable, onDelete = ReferenceOption.CASCADE)
    val name = varchar("name", 32).uniqueIndex()
    val releasedAt = timestamp("released_at").nullable()
    val createdAt = timestamp("created_at").clientDefault { Clock.System.now() }

    init {
        index(false, businessId, releasedAt)
        index(false, releasedAt)
    }
}
