package com.bookk.business.domain.api.employee.operation

import com.bookk.business.domain.api.employee.entity.BookableEmployee
import kotlin.uuid.Uuid

interface GetBookableEmployees {
    suspend operator fun invoke(businessId: Uuid): Result<List<BookableEmployee>>
}
