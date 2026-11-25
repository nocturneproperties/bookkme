package com.bookk.business.data.orm.table

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table

internal const val NAME_TOKEN_LENGTH = 16

internal object ClientNameTokenTable : Table("client_name_token") {
    val businessId = uuid("business_id")
    val token = binary("token", NAME_TOKEN_LENGTH)
    val clientId = reference("client_id", ClientTable, onDelete = ReferenceOption.CASCADE).index()

    override val primaryKey = PrimaryKey(businessId, token, clientId)
}
