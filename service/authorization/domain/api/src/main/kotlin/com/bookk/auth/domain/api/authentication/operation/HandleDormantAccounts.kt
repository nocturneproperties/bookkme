package com.bookk.auth.domain.api.authentication.operation

interface HandleDormantAccounts {
    suspend operator fun invoke(): Result<Unit>
}
