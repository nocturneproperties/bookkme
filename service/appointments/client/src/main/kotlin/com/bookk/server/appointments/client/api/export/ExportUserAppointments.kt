package com.bookk.server.appointments.client.api.export

import kotlin.uuid.Uuid

interface ExportUserAppointments {
    suspend operator fun invoke(userId: Uuid): Result<AppointmentsUserDataExport>
}
