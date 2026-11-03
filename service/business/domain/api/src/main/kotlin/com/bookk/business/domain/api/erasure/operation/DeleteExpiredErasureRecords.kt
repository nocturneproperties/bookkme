package com.bookk.business.domain.api.erasure.operation

interface DeleteExpiredErasureRecords {
    suspend operator fun invoke(): Result<Unit>
}
