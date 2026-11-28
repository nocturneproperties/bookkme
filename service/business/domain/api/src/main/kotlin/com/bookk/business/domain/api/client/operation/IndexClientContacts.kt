package com.bookk.business.domain.api.client.operation

interface IndexClientContacts {
    suspend operator fun invoke(): Result<Unit>
}
