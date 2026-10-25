package com.bookk.appointments.data.orm.table

import com.bookk.core.data.database.BaseUUIDTable
import org.jetbrains.exposed.v1.core.ReferenceOption

object PriceAdjustmentServicesTable : BaseUUIDTable("appointment_price_adjustment_services") {
    val priceAdjustmentId = reference("price_adjustment_id", PriceAdjustmentTable, ReferenceOption.CASCADE).index()
    val serviceId = uuid("service_id").index()
    val serviceName = varchar("service_name", 1024)
    val serviceGroupId = uuid("service_group_id")
    val priceCurrency = varchar("price_currency", 3)
    val priceUnscaled = long("price_unscaled")
    val priceScale = integer("price_scale")
    val durationMinutes = long("duration_minutes")
}
