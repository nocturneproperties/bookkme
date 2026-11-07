package com.bookk.appointments.domain.datasource

import com.bookk.appointments.domain.api.entity.Appointment
import com.bookk.appointments.domain.api.entity.AppointmentRequest
import kotlin.uuid.Uuid

interface UserDataExportDataSource {
    suspend fun getAppointmentsOfUser(userId: Uuid): List<Appointment>
    suspend fun getRequestsOfUser(userId: Uuid): List<AppointmentRequest>
    suspend fun getBusinessNames(businessIds: Collection<Uuid>): Map<Uuid, String>
}
