package com.bookk.business.data.orm.table

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.UuidTable
import org.jetbrains.exposed.v1.datetime.timestamp
import kotlin.time.Clock

private const val BLIND_INDEX_LENGTH = 44

internal object ClientTable : UuidTable("client") {
    val businessId = reference("business_id", BusinessTable, onDelete = ReferenceOption.CASCADE).index()
    val name = varchar("name", 512).nullable()
    val lastName = varchar("lastname", 512).nullable()
    val phone = varchar("phone", 512).nullable()
    val email = varchar("email", 512).nullable().index()
    val userId = uuid("user_id").nullable().index()
    val sourceUpdatedAt = timestamp("source_updated_at").nullable()
    val description = varchar("description", 1024).nullable()
    val nameEncrypted = binary("name_encrypted", 2080).nullable()
    val lastNameEncrypted = binary("lastname_encrypted", 2080).nullable()
    val phoneEncrypted = binary("phone_encrypted", 2080).nullable()
    val emailEncrypted = binary("email_encrypted", 2080).nullable()
    val descriptionEncrypted = binary("description_encrypted", 4128).nullable()
    val emailIndex = char("email_index", BLIND_INDEX_LENGTH).nullable()
    val lastActivityAt = timestamp("last_activity_at").clientDefault { Clock.System.now() }
    val deletionHold = bool("deletion_hold").default(false)

    init {
        uniqueIndex(businessId, userId)
        uniqueIndex(businessId, email)
        uniqueIndex(businessId, emailIndex)
        index(false, businessId, lastActivityAt)
    }
}
