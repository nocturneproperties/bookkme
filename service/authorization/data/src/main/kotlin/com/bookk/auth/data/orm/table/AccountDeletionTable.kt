package com.bookk.auth.data.orm.table

import com.bookk.auth.domain.api.delete_account.entity.DeletionReminder
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestamp

internal object AccountDeletionTable : Table("account_deletion") {
    val authId = reference("auth_id", AuthenticationTable, onDelete = ReferenceOption.CASCADE)
    val requestedAt = timestamp("requested_at")
    val deletesAt = timestamp("deletes_at").index()
    val reminder = enumerationByName<DeletionReminder>("reminder", 16).default(DeletionReminder.NONE)

    override val primaryKey = PrimaryKey(authId)
}
