package com.bookk.server.appointments.client.api.export

import com.bookk.server.appointments.client.api.export.AppointmentsUserDataExport.PriceAdjustmentExport
import com.bookk.server.appointments.client.api.export.AppointmentsUserDataExport.ServiceExport
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Serializable
data class AppointmentsBusinessDataExport(
    @ProtoNumber(1) val settings: SettingsExport?,
    @ProtoNumber(2) val appointments: List<AppointmentExport>,
    @ProtoNumber(3) val pendingRequests: List<RequestExport>,
) {
    @Serializable
    data class SettingsExport(
        @ProtoNumber(1) val automaticApproval: Boolean,
        @ProtoNumber(2) val automaticCompletion: Boolean,
        @ProtoNumber(3) val breakBetweenAppointmentsMinutes: Int,
        @ProtoNumber(4) val appointmentNote: String,
    )

    @Serializable
    data class EmployeeExport(
        @ProtoNumber(1) val id: Uuid,
        @ProtoNumber(2) val name: String,
    )

    @Serializable
    data class ClientExport(
        @ProtoNumber(1) val id: Uuid,
        @ProtoNumber(2) val name: String,
        @ProtoNumber(3) val phone: String?,
        @ProtoNumber(4) val email: String?,
        @ProtoNumber(5) val hasAccount: Boolean,
    )

    @Serializable
    data class AppointmentExport(
        @ProtoNumber(1) val id: Uuid,
        @ProtoNumber(2) val start: Instant,
        @ProtoNumber(3) val end: Instant,
        @ProtoNumber(4) val status: String,
        @ProtoNumber(5) val services: List<ServiceExport>,
        @ProtoNumber(6) val employee: EmployeeExport,
        @ProtoNumber(7) val client: ClientExport,
        @ProtoNumber(8) val note: String?,
        @ProtoNumber(9) val cancellationReason: String?,
        @ProtoNumber(10) val priceAdjustment: PriceAdjustmentExport?,
        @ProtoNumber(11) val completedBy: String?,
    )

    @Serializable
    data class RequestExport(
        @ProtoNumber(1) val id: Uuid,
        @ProtoNumber(2) val start: Instant,
        @ProtoNumber(3) val end: Instant,
        @ProtoNumber(4) val services: List<ServiceExport>,
        @ProtoNumber(5) val employee: EmployeeExport,
        @ProtoNumber(6) val client: ClientExport,
        @ProtoNumber(7) val note: String?,
    )

    companion object {
        fun stub(
            settings: SettingsExport? = null,
            appointments: List<AppointmentExport> = emptyList(),
            pendingRequests: List<RequestExport> = emptyList(),
        ) = AppointmentsBusinessDataExport(settings, appointments, pendingRequests)
    }
}
