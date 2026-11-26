package com.bookk.auth.data.orm.table

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.UuidTable
import org.jetbrains.exposed.v1.datetime.timestamp
import kotlin.time.Clock

internal object TelegramIdentityTable : UuidTable("telegram_identity") {
    val authId = reference("auth_id", AuthenticationTable, onDelete = ReferenceOption.CASCADE).uniqueIndex()
    val telegramIdIndex = char("telegram_id_index", 44).uniqueIndex()
    val usernameIndex = char("username_index", 44).nullable()
    val createdAt = timestamp("created_at").clientDefault { Clock.System.now() }
}
