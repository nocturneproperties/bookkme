package com.bookk.server.business.client.impl.operation

import com.bookk.business.domain.api.export.BusinessUserDataExport
import com.bookk.business.domain.api.export.ExportUserBusinessData
import com.bookk.core.client.bodyOrThrow
import com.bookk.core.domain.entity.runSuspendCatching
import com.bookk.server.business.client.impl.BusinessRouting
import io.ktor.client.HttpClient
import io.ktor.client.plugins.resources.get
import kotlin.uuid.Uuid

internal class ExportUserBusinessDataClientImpl(
    private val httpClient: HttpClient,
) : ExportUserBusinessData {

    override suspend fun invoke(userId: Uuid): Result<BusinessUserDataExport> = runSuspendCatching {
        httpClient.get(BusinessRouting.Api.Internal.Export.User(userId = userId)).bodyOrThrow()
    }
}
