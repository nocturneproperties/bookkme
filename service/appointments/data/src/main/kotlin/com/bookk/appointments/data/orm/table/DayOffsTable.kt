package com.bookk.appointments.data.orm.table

import com.bookk.core.data.database.BaseUUIDTable
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.datetime.date

object DayOffsTable: BaseUUIDTable("appointment_day_offs") {
    val businessId = reference("business_id", AppointmentBusinessTable, onDelete = ReferenceOption.CASCADE).index()
    val startDate = date("start_date")
    val endDate = date("end_date")

    init {
        index(false, businessId, startDate)
    }
}