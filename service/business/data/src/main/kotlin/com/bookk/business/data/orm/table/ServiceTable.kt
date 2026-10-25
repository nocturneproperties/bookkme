package com.bookk.business.data.orm.table

import com.bookk.core.data.database.BaseUUIDTable
import org.jetbrains.exposed.v1.core.ReferenceOption

object ServiceTable : BaseUUIDTable("service") {
    val businessId = reference("business_id", BusinessTable, onDelete = ReferenceOption.CASCADE).index()
    val groupId = reference("group_id", ServiceGroupTable, onDelete = ReferenceOption.CASCADE).index()
    val name = varchar("name", 512)
    val duration = integer("duration")
    val priceCurrency = varchar("price_currency", 3)
    val priceUnscaled = long("price_unscaled")
    val priceScale = integer("price_scale")
    val available = bool("available").default(true)

    init {
        index(true, businessId, groupId, name)
    }
}