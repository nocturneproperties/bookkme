package com.bookk.business.domain.api.business.operation

import com.bookk.business.domain.api.business.entity.BusinessResource
import library.permissions.ResourcePermission
import kotlin.uuid.Uuid

interface GetBusinessPermission {
    suspend operator fun invoke(userId: Uuid, businessId: Uuid, resource: BusinessResource): Result<ResourcePermission>
}
