package com.bookk.appointments.domain.api.operation

import kotlin.uuid.Uuid

interface RemoveSavedBusiness {
    suspend operator fun invoke(userId: Uuid, businessId: Uuid): Result<Unit>
}
