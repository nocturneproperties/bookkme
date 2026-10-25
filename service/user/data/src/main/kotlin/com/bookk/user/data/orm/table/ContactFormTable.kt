package com.bookk.user.data.orm.table

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.UuidTable
import org.jetbrains.exposed.v1.datetime.timestamp
import kotlin.time.Clock

object ContactFormTable : UuidTable("contact_form") {
    val userId = reference("user_id", UserTable, onDelete = ReferenceOption.CASCADE).index()
    val text = text("text")
    val usageLogs = text("usage_logs").nullable()
    val createdAt = timestamp("created_at").clientDefault { Clock.System.now() }
    val updatedAt = timestamp("updated_at").clientDefault { Clock.System.now() }
    val status = byte("status")
}