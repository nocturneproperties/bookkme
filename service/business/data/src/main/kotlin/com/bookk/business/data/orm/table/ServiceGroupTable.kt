package com.bookk.business.data.orm.table

import com.bookk.core.data.database.BaseUUIDTable
import org.jetbrains.exposed.v1.core.ReferenceOption

internal object ServiceGroupTable : BaseUUIDTable("service_group") {
    val businessId = reference("business_id", BusinessTable, onDelete = ReferenceOption.CASCADE).index()
    val name = varchar("name", 512)

    init {
        index(true, businessId, name)
    }
}