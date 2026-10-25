package com.bookk.business.data.orm.table

import com.bookk.core.data.database.BaseUUIDTable
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.datetime.time

object EmployeeWorkingHoursTable : BaseUUIDTable("employee_working_hours") {
    val employeeId = reference("employee_id", EmployeeTable, onDelete = ReferenceOption.CASCADE).index()
    val dayOfWeek = byte("day_of_week")
    val startTime = time("start_time")
    val endTime = time("end_time")
}
