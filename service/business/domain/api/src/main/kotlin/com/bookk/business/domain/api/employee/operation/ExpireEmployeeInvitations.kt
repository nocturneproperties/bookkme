package com.bookk.business.domain.api.employee.operation

interface ExpireEmployeeInvitations {
    suspend operator fun invoke(): Result<Unit>
}
