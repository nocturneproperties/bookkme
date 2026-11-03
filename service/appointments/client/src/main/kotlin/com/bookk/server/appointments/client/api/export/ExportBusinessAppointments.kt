package com.bookk.server.appointments.client.api.export

import kotlin.uuid.Uuid

interface ExportBusinessAppointments {
    suspend operator fun invoke(businessId: Uuid): Result<AppointmentsBusinessDataExport>
}
