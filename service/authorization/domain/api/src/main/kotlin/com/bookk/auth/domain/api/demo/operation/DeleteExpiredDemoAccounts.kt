package com.bookk.auth.domain.api.demo.operation

interface DeleteExpiredDemoAccounts {
    suspend operator fun invoke(): Result<Unit>
}
