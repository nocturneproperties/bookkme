package com.bookk.business.domain.api.business.operation

interface DeleteDayOffsInThePast {
    suspend operator fun invoke(): Result<Unit>
}
