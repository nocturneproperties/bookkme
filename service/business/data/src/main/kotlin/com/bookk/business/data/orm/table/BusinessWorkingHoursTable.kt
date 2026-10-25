package com.bookk.business.data.orm.table

import com.bookk.core.data.database.BaseUUIDTable
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.datetime.time

object BusinessWorkingHoursTable : BaseUUIDTable("business_working_hours") {
    val businessId = reference("business_id", BusinessTable, onDelete = ReferenceOption.CASCADE).index()
    val dayOfWeek = byte("day_of_week")
    val startTime = time("start_time")
    val endTime = time("end_time")
}
