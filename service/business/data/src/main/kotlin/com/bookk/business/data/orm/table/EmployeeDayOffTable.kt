package com.bookk.business.data.orm.table

import com.bookk.core.data.database.BaseUUIDTable
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.datetime.date

object EmployeeDayOffTable : BaseUUIDTable("employee_day_offs") {
    val employeeId = reference("employee_id", EmployeeTable, onDelete = ReferenceOption.CASCADE).index()
    val startDate = date("start_date")
    val endDate = date("end_date")

    init {
        index(false, employeeId, startDate)
    }
}
