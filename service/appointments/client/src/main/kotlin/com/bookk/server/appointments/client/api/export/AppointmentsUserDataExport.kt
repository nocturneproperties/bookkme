package com.bookk.server.appointments.client.api.export

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Serializable
data class AppointmentsUserDataExport(
    @ProtoNumber(1) val appointments: List<AppointmentExport>,
    @ProtoNumber(2) val requests: List<AppointmentRequestExport>,
) {
    @Serializable
    enum class Role {
        @ProtoNumber(0)
        CLIENT,

        @ProtoNumber(1)
        EMPLOYEE,

        @ProtoNumber(2)
        BOOKER,
    }

    @Serializable
    data class AppointmentExport(
        @ProtoNumber(1) val id: Uuid,
        @ProtoNumber(2) val businessId: Uuid,
        @ProtoNumber(3) val businessName: String,
        @ProtoNumber(4) val roles: List<Role>,
        @ProtoNumber(5) val start: Instant,
        @ProtoNumber(6) val end: Instant,
        @ProtoNumber(7) val status: String,
        @ProtoNumber(8) val services: List<ServiceExport>,
        @ProtoNumber(9) val employeeName: String,
        @ProtoNumber(10) val client: ClientDetailsExport?,
        @ProtoNumber(11) val cancellationReason: String?,
        @ProtoNumber(12) val priceAdjustment: PriceAdjustmentExport?,
        @ProtoNumber(13) val completedBy: String?,
    ) {
        companion object {
            fun stub(
                id: Uuid = Uuid.random(),
                businessId: Uuid = Uuid.random(),
                businessName: String = "Studio",
                roles: List<Role> = listOf(Role.CLIENT),
                start: Instant = Instant.fromEpochMilliseconds(0),
                end: Instant = Instant.fromEpochMilliseconds(0),
                status: String = "SCHEDULED",
                services: List<ServiceExport> = emptyList(),
                employeeName: String = "Olena Koval",
                client: ClientDetailsExport? = null,
                cancellationReason: String? = null,
                priceAdjustment: PriceAdjustmentExport? = null,
                completedBy: String? = null,
            ) = AppointmentExport(id, businessId, businessName, roles, start, end, status, services, employeeName, client, cancellationReason, priceAdjustment, completedBy)
        }
    }

    @Serializable
    data class AppointmentRequestExport(
        @ProtoNumber(1) val id: Uuid,
        @ProtoNumber(2) val businessId: Uuid,
        @ProtoNumber(3) val businessName: String,
        @ProtoNumber(4) val roles: List<Role>,
        @ProtoNumber(5) val start: Instant,
        @ProtoNumber(6) val end: Instant,
        @ProtoNumber(7) val status: String,
        @ProtoNumber(8) val services: List<ServiceExport>,
        @ProtoNumber(9) val employeeName: String,
        @ProtoNumber(10) val client: ClientDetailsExport?,
        @ProtoNumber(11) val declineReason: String?,
    ) {
        companion object {
            fun stub(
                id: Uuid = Uuid.random(),
                businessId: Uuid = Uuid.random(),
                businessName: String = "Studio",
                roles: List<Role> = listOf(Role.CLIENT),
                start: Instant = Instant.fromEpochMilliseconds(0),
                end: Instant = Instant.fromEpochMilliseconds(0),
                status: String = "PENDING",
                services: List<ServiceExport> = emptyList(),
                employeeName: String = "Olena Koval",
                client: ClientDetailsExport? = null,
                declineReason: String? = null,
            ) = AppointmentRequestExport(id, businessId, businessName, roles, start, end, status, services, employeeName, client, declineReason)
        }
    }

    @Serializable
    data class ServiceExport(
        @ProtoNumber(1) val name: String,
        @ProtoNumber(2) val price: String,
        @ProtoNumber(3) val durationMinutes: Long,
    )

    @Serializable
    data class ClientDetailsExport(
        @ProtoNumber(1) val name: String,
        @ProtoNumber(2) val phone: String?,
        @ProtoNumber(3) val email: String?,
        @ProtoNumber(4) val note: String?,
    )

    @Serializable
    data class PriceAdjustmentExport(
        @ProtoNumber(1) val price: String,
        @ProtoNumber(2) val additionalServices: List<ServiceExport>,
        @ProtoNumber(3) val reason: String?,
    )

    companion object {
        fun stub(
            appointments: List<AppointmentExport> = emptyList(),
            requests: List<AppointmentRequestExport> = emptyList(),
        ) = AppointmentsUserDataExport(appointments, requests)
    }
}
