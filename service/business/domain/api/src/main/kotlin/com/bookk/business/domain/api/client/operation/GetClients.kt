package com.bookk.business.domain.api.client.operation

import com.bookk.business.domain.api.client.entity.ClientRemote
import kotlin.uuid.Uuid

interface GetClients {
    suspend operator fun invoke(requestUserId: Uuid, businessId: Uuid): Result<List<ClientRemote>>
}