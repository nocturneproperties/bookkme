package com.bookk.server.business.client.impl.operation

import com.bookk.business.domain.api.business.entity.Business
import com.bookk.business.domain.api.business.operation.GetBusinessDetails
import com.bookk.core.client.bodyOrThrow
import com.bookk.core.domain.entity.runSuspendCatching
import com.bookk.server.business.client.impl.BusinessRouting
import io.ktor.client.HttpClient
import io.ktor.client.plugins.resources.get
import kotlin.uuid.Uuid

internal class GetBusinessDetailsClientImpl(
    private val httpClient: HttpClient
) : GetBusinessDetails {

    override suspend fun invoke(id: Uuid): Result<Business> = runSuspendCatching {
        httpClient.get(BusinessRouting.Api.Internal.Business.Id(id = id)).bodyOrThrow()
    }
}
