package com.bookk.business.domain.api.client.operation

import com.bookk.business.domain.api.client.entity.ClientPage
import kotlin.uuid.Uuid

interface GetClients {
    suspend operator fun invoke(
        requestUserId: Uuid,
        businessId: Uuid,
        limit: Int?,
        cursor: String?,
        query: String?,
    ): Result<ClientPage>
}
