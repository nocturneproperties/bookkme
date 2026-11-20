package com.bookk.user.data.orm.table

import org.jetbrains.exposed.v1.core.dao.id.UuidTable
import org.jetbrains.exposed.v1.datetime.timestamp
import kotlin.time.Clock
import kotlin.time.Clock.System

private const val BLIND_INDEX_LENGTH = 44

object UserTable : UuidTable("profile") {
    val nameEncrypted = binary("name_encrypted", 1100)
    val lastNameEncrypted = binary("last_name_encrypted", 1100)
    val emailEncrypted = binary("email_encrypted", 1320)
    val phoneEncrypted = binary("phone_encrypted", 2080).nullable()
    val telegramUserIdEncrypted = binary("telegram_user_id_encrypted", 128).nullable()
    val emailIndex = char("email_index", BLIND_INDEX_LENGTH).uniqueIndex()
    val phoneIndex = char("phone_index", BLIND_INDEX_LENGTH).nullable().uniqueIndex()
    val createdAt = timestamp("created_at").clientDefault { System.now() }
    val updatedAt = timestamp("updated_at").clientDefault { Clock.System.now() }
}
