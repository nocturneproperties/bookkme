package com.bookk.business.domain.api.client.operation

interface DeleteInactiveClients {
    suspend operator fun invoke(): Result<Unit>
}
