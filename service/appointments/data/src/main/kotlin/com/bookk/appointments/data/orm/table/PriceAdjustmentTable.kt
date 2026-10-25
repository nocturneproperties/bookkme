package com.bookk.appointments.data.orm.table

import com.bookk.core.data.database.BaseUUIDTable
import org.jetbrains.exposed.v1.core.ReferenceOption

object PriceAdjustmentTable : BaseUUIDTable("appointment_price_adjustment") {
    val appointmentId = reference("appointment_id", AppointmentTable, ReferenceOption.CASCADE).uniqueIndex()
    val priceCurrency = varchar("price_currency", 3)
    val priceUnscaled = long("price_unscaled")
    val priceScale = integer("price_scale")
    val reason = varchar("reason", 2048).nullable()
}
