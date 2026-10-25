package com.bookk.business.domain.api.client.operation

import kotlin.uuid.Uuid

interface GetClientBusinessIds {
    suspend operator fun invoke(userId: Uuid): Result<List<Uuid>>
}
