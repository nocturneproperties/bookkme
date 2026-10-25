package com.bookk.business.data.orm.table

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.UuidTable

object EmployeeCanProvideServiceTable : UuidTable("employee_can_provide_service") {
    val employeeId = reference("employee_id", EmployeeTable, onDelete = ReferenceOption.CASCADE)
    val serviceId = reference("service_id", ServiceTable, onDelete = ReferenceOption.CASCADE).index()
    val businessId = reference("business_id", BusinessTable, onDelete = ReferenceOption.CASCADE).index()

    init {
        index(isUnique = true, employeeId, serviceId)
    }
}
