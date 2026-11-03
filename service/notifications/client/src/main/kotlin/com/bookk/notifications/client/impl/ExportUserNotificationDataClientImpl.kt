package com.bookk.notifications.client.impl

import com.bookk.core.client.bodyOrThrow
import com.bookk.core.domain.entity.runSuspendCatching
import com.bookk.notifications.client.api.export.ExportUserNotificationData
import com.bookk.notifications.client.api.export.NotificationsUserDataExport
import io.ktor.client.HttpClient
import io.ktor.client.plugins.resources.get
import kotlin.uuid.Uuid

internal class ExportUserNotificationDataClientImpl(
    private val httpClient: HttpClient,
) : ExportUserNotificationData {

    override suspend fun invoke(userId: Uuid): Result<NotificationsUserDataExport> = runSuspendCatching {
        httpClient.get(NotificationsRouting.Api.Internal.Export.User(userId = userId)).bodyOrThrow()
    }
}
