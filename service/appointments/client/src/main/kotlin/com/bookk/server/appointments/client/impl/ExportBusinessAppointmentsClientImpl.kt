package com.bookk.server.appointments.client.impl

import com.bookk.core.client.bodyOrThrow
import com.bookk.core.domain.entity.runSuspendCatching
import com.bookk.server.appointments.client.api.export.AppointmentsBusinessDataExport
import com.bookk.server.appointments.client.api.export.ExportBusinessAppointments
import io.ktor.client.HttpClient
import io.ktor.client.plugins.resources.get
import kotlin.uuid.Uuid

internal class ExportBusinessAppointmentsClientImpl(
    private val httpClient: HttpClient,
) : ExportBusinessAppointments {

    override suspend fun invoke(businessId: Uuid): Result<AppointmentsBusinessDataExport> = runSuspendCatching {
        httpClient.get(AppointmentsRouting.Api.Internal.Export.Business(businessId = businessId)).bodyOrThrow()
    }
}
