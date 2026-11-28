package com.bookk.business.domain.api.business.operation

interface DeleteReleasedBusinessLinks {
    suspend operator fun invoke(): Result<Unit>
}
