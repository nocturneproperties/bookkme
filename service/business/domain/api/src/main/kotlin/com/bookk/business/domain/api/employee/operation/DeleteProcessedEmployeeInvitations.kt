package com.bookk.business.domain.api.employee.operation

interface DeleteProcessedEmployeeInvitations {
    suspend operator fun invoke(): Result<Unit>
}
