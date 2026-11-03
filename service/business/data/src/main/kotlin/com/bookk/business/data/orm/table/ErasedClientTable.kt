package com.bookk.business.data.orm.table

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestamp

internal object ErasedClientTable : Table("erased_client") {
    val clientId = uuid("client_id")
    val businessId = uuid("business_id")
    val erasedAt = timestamp("erased_at")
    val reapplyRequested = bool("reapply_requested").default(false)

    override val primaryKey = PrimaryKey(clientId)
}
