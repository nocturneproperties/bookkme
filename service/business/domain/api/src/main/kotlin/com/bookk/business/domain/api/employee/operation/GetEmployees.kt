package com.bookk.business.domain.api.employee.operation

import com.bookk.business.domain.api.employee.entity.Employee
import kotlin.uuid.Uuid

interface GetEmployees {
    suspend operator fun invoke(userId: Uuid, businessId: Uuid): Result<List<Employee>>
}
