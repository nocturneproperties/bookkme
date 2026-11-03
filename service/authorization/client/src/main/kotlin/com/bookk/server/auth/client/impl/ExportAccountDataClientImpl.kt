package com.bookk.server.auth.client.impl

import com.bookk.core.client.bodyOrThrow
import com.bookk.core.domain.entity.runSuspendCatching
import com.bookk.server.auth.client.export.AccountDataExport
import com.bookk.server.auth.client.export.ExportAccountData
import io.ktor.client.HttpClient
import io.ktor.client.plugins.resources.get
import kotlin.uuid.Uuid

internal class ExportAccountDataClientImpl(
    private val httpClient: HttpClient,
) : ExportAccountData {

    override suspend fun invoke(userId: Uuid): Result<AccountDataExport> = runSuspendCatching {
        httpClient.get(AuthRouting.Api.Internal.Export.Account(userId = userId)).bodyOrThrow()
    }
}
