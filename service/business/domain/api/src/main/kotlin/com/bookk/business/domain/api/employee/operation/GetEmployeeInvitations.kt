package com.bookk.business.domain.api.employee.operation

import com.bookk.business.domain.api.employee.entity.EmployeeInvitation
import kotlin.uuid.Uuid

interface GetEmployeeInvitations {
    suspend operator fun invoke(userId: Uuid, businessId: Uuid): Result<List<EmployeeInvitation>>
}
