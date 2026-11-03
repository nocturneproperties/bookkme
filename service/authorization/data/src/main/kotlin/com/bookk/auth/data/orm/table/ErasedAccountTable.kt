package com.bookk.auth.data.orm.table

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestamp

internal object ErasedAccountTable : Table("erased_account") {
    val userId = uuid("user_id")
    val erasedAt = timestamp("erased_at")
    val reapplyRequested = bool("reapply_requested").default(false)

    override val primaryKey = PrimaryKey(userId)
}
