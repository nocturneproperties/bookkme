package com.bookk.server.business.client.impl.operation

import com.bookk.business.domain.api.business.entity.BusinessResource
import com.bookk.business.domain.api.business.operation.GetBusinessPermission
import com.bookk.core.client.bodyOrThrow
import com.bookk.core.domain.entity.runSuspendCatching
import com.bookk.server.business.client.impl.BusinessRouting
import io.ktor.client.HttpClient
import io.ktor.client.plugins.resources.get
import library.permissions.ResourcePermission
import kotlin.uuid.Uuid

internal class GetBusinessPermissionClientImpl(
    private val httpClient: HttpClient
) : GetBusinessPermission {

    override suspend fun invoke(userId: Uuid, businessId: Uuid, resource: BusinessResource): Result<ResourcePermission> = runSuspendCatching {
        httpClient.get(
            BusinessRouting.Api.Internal.Business.Id.Permissions(
                parent = BusinessRouting.Api.Internal.Business.Id(id = businessId),
                userId = userId,
                resource = resource
            )
        ).bodyOrThrow()
    }
}
