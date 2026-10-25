package com.bookk.business.domain.api.service.operation

import com.bookk.business.domain.api.service.entity.Service
import kotlin.uuid.Uuid

interface GetServices {
    suspend operator fun invoke(businessId: Uuid): Result<List<Service>>
}