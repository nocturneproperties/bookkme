package com.bookk.business.domain.datasource

import com.bookk.business.domain.api.employee.entity.Employee
import com.bookk.business.domain.api.employee.entity.EmployeeUpdateModel
import kotlin.time.Instant
import kotlin.uuid.Uuid

interface EmployeeDataSource {
    suspend fun createEmployee(employee: Employee): Employee
    suspend fun updateEmployee(model: EmployeeUpdateModel): Employee
    suspend fun getEmployees(businessId: Uuid): List<Employee>
    suspend fun getEmployee(businessId: Uuid, id: Uuid): Employee?
    suspend fun setSuspendedAt(id: Uuid, suspendedAt: Instant?): Employee
    suspend fun getEmployeeByUserId(businessId: Uuid, userId: Uuid): Employee?
    suspend fun deleteEmployee(businessId: Uuid, id: Uuid): Boolean
    suspend fun updateIntegratedEmployees(
        userId: Uuid,
        name: String,
        lastName: String,
        email: String,
        phone: String?,
        updatedAt: Instant
    ): Int

    suspend fun getServiceIds(employeeId: Uuid): List<Uuid>
    suspend fun getEmployeesByService(serviceId: Uuid): List<Employee>
    suspend fun anonymizeEmployeesByUserId(userId: Uuid): Int
}
