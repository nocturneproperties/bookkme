package com.bookk.appointments.domain.api.operation

import kotlin.uuid.Uuid

interface DeleteModule {
    suspend operator fun invoke(businessId: Uuid): Result<Unit>
}