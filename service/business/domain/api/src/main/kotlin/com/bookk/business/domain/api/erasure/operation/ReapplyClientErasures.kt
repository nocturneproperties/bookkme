package com.bookk.business.domain.api.erasure.operation

interface ReapplyClientErasures {
    suspend operator fun invoke(): Result<Unit>
}
