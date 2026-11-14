package com.bookk.user.data.orm.table

import org.jetbrains.exposed.v1.core.dao.id.UuidTable
import org.jetbrains.exposed.v1.datetime.timestamp
import kotlin.time.Clock
import kotlin.time.Clock.System

private const val BLIND_INDEX_LENGTH = 44

object UserTable : UuidTable("profile") {
    val name = varchar("name", 255).nullable()
    val lastName = varchar("last_name", 255).nullable()
    val email = varchar("email", 320).nullable().uniqueIndex()
    val phone = varchar("phone", 512).nullable().uniqueIndex()
    val nameEncrypted = binary("name_encrypted", 1100).nullable()
    val lastNameEncrypted = binary("last_name_encrypted", 1100).nullable()
    val emailEncrypted = binary("email_encrypted", 1320).nullable()
    val phoneEncrypted = binary("phone_encrypted", 2080).nullable()
    val emailIndex = char("email_index", BLIND_INDEX_LENGTH).nullable().uniqueIndex()
    val phoneIndex = char("phone_index", BLIND_INDEX_LENGTH).nullable().uniqueIndex()
    val createdAt = timestamp("created_at").clientDefault { System.now() }
    val updatedAt = timestamp("updated_at").clientDefault { Clock.System.now() }
}
