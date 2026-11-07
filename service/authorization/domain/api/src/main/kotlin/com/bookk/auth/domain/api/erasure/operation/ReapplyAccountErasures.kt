package com.bookk.auth.domain.api.erasure.operation

interface ReapplyAccountErasures {
    suspend operator fun invoke(): Result<Unit>
}
