package com.bookk.appointments.data.orm.table

import com.bookk.core.data.database.BaseUUIDTable
import org.jetbrains.exposed.v1.core.ReferenceOption

object AppointmentServicesTable : BaseUUIDTable("appointment_services") {
    val appointmentId = reference("appointment_id", AppointmentTable, ReferenceOption.CASCADE).index()
    val serviceId = uuid("service_id").index()
    val serviceName = varchar("service_name", 1024)
    val serviceGroupId = uuid("service_group_id")
    val priceCurrency = varchar("price_currency", 3)
    val priceUnscaled = long("price_unscaled")
    val priceScale = integer("price_scale")
    val durationMinutes = long("duration_minutes")
}