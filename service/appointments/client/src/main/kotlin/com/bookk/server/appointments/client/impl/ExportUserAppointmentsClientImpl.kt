package com.bookk.server.appointments.client.impl

import com.bookk.core.client.bodyOrThrow
import com.bookk.core.domain.entity.runSuspendCatching
import com.bookk.server.appointments.client.api.export.AppointmentsUserDataExport
import com.bookk.server.appointments.client.api.export.ExportUserAppointments
import io.ktor.client.HttpClient
import io.ktor.client.plugins.resources.get
import kotlin.uuid.Uuid

internal class ExportUserAppointmentsClientImpl(
    private val httpClient: HttpClient,
) : ExportUserAppointments {

    override suspend fun invoke(userId: Uuid): Result<AppointmentsUserDataExport> = runSuspendCatching {
        httpClient.get(AppointmentsRouting.Api.Internal.Export.User(userId = userId)).bodyOrThrow()
    }
}
