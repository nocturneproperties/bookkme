package com.bookk.business.data.orm.table

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.UuidTable
import org.jetbrains.exposed.v1.datetime.timestamp
import kotlin.time.Clock

private const val BLIND_INDEX_LENGTH = 44

internal object ClientTable : UuidTable("client") {
    val businessId = reference("business_id", BusinessTable, onDelete = ReferenceOption.CASCADE).index()
    val userId = uuid("user_id").nullable().index()
    val sourceUpdatedAt = timestamp("source_updated_at").nullable()
    val nameEncrypted = binary("name_encrypted", 2080)
    val lastNameEncrypted = binary("lastname_encrypted", 2080)
    val phoneEncrypted = binary("phone_encrypted", 2080).nullable()
    val emailEncrypted = binary("email_encrypted", 2080).nullable()
    val descriptionEncrypted = binary("description_encrypted", 4128).nullable()
    val emailIndex = char("email_index", BLIND_INDEX_LENGTH).nullable()
    val phoneIndex = char("phone_index", BLIND_INDEX_LENGTH).nullable()
    val createdAt = timestamp("created_at").clientDefault { Clock.System.now() }
    val lastActivityAt = timestamp("last_activity_at").clientDefault { Clock.System.now() }
    val deletionHold = bool("deletion_hold").default(false)

    init {
        uniqueIndex(businessId, userId)
        uniqueIndex(businessId, emailIndex)
        index(false, businessId, lastActivityAt)
        index(false, businessId, createdAt)
        index(false, businessId, phoneIndex)
    }
}
