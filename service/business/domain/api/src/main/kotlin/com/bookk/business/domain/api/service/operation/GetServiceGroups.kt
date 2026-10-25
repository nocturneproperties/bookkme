package com.bookk.business.domain.api.service.operation

import com.bookk.business.domain.api.service.entity.ServiceGroup
import kotlin.uuid.Uuid

interface GetServiceGroups {
    suspend operator fun invoke(businessId: Uuid): Result<List<ServiceGroup>>
}