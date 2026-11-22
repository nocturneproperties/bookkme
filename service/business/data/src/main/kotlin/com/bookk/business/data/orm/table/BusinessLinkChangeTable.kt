package com.bookk.business.data.orm.table

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.UuidTable
import org.jetbrains.exposed.v1.datetime.timestamp

object BusinessLinkChangeTable : UuidTable("business_link_change") {
    val businessId = reference("business_id", BusinessTable, onDelete = ReferenceOption.CASCADE)
    val changedAt = timestamp("changed_at")

    init {
        index(false, businessId, changedAt)
        index(false, changedAt)
    }
}
