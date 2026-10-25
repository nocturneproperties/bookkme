package com.bookk.server.business.client.impl.operation

import com.bookk.business.domain.api.service.entity.Service
import com.bookk.business.domain.api.service.entity.ServicesByIdsRequest
import com.bookk.business.domain.api.service.operation.GetServicesByIds
import com.bookk.core.client.bodyOrThrow
import com.bookk.core.domain.entity.runSuspendCatching
import com.bookk.server.business.client.impl.BusinessRouting
import io.ktor.client.HttpClient
import io.ktor.client.plugins.resources.post
import io.ktor.client.request.setBody
import kotlin.uuid.Uuid

internal class GetServicesByIdsClientImpl(
    private val httpClient: HttpClient
) : GetServicesByIds {

    override suspend fun invoke(businessId: Uuid, serviceIds: List<Uuid>): Result<List<Service>> = runSuspendCatching {
        httpClient.post(
            BusinessRouting.Api.Internal.Business.Id.Services(
                parent = BusinessRouting.Api.Internal.Business.Id(id = businessId)
            )
        ) {
            setBody(ServicesByIdsRequest(serviceIds = serviceIds))
        }.bodyOrThrow()
    }
}
