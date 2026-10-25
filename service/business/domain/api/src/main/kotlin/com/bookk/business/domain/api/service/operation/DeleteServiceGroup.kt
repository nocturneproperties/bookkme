package com.bookk.business.domain.api.service.operation

import kotlin.uuid.Uuid

interface DeleteServiceGroup {
    suspend operator fun invoke(requestUserId: Uuid, businessId: Uuid, id: Uuid): Result<Unit>
}