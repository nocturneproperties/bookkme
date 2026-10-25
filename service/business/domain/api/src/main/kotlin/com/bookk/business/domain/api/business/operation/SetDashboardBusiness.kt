package com.bookk.business.domain.api.business.operation

import kotlin.uuid.Uuid

interface SetDashboardBusiness {
    suspend operator fun invoke(userId: Uuid, businessId: Uuid): Result<Unit>
}
