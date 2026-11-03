package com.bookk.auth.domain.api.erasure.operation

interface DeleteExpiredErasureRecords {
    suspend operator fun invoke(): Result<Unit>
}
