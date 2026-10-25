package com.bookk.business.data.orm.table

import com.bookk.core.data.database.BaseUUIDTable
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.datetime.date

object BusinessDayOffTable : BaseUUIDTable("business_day_offs") {
    val businessId = reference("business_id", BusinessTable, onDelete = ReferenceOption.CASCADE).index()
    val startDate = date("start_date")
    val endDate = date("end_date")

    init {
        index(false, businessId, startDate)
    }
}
