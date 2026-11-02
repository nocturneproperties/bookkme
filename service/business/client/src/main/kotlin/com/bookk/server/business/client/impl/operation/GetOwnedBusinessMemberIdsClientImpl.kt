package com.bookk.server.business.client.impl.operation

import com.bookk.business.domain.api.employee.operation.GetOwnedBusinessMemberIds
import com.bookk.core.client.bodyOrThrow
import com.bookk.core.domain.entity.runSuspendCatching
import com.bookk.server.business.client.impl.BusinessRouting
import io.ktor.client.HttpClient
import io.ktor.client.plugins.resources.get
import kotlin.uuid.Uuid

internal class GetOwnedBusinessMemberIdsClientImpl(
    private val httpClient: HttpClient,
) : GetOwnedBusinessMemberIds {

    override suspend fun invoke(ownerUserId: Uuid): Result<List<Uuid>> = runSuspendCatching {
        httpClient.get(
            BusinessRouting.Api.Internal.Owner.Members(
                parent = BusinessRouting.Api.Internal.Owner(),
                userId = ownerUserId,
            ),
        ).bodyOrThrow()
    }
}
