package com.bookk.business.domain.api.export

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Serializable
data class BusinessUserDataExport(
    @ProtoNumber(1) val ownedBusiness: OwnedBusinessExport?,
    @ProtoNumber(2) val employments: List<EmploymentExport>,
    @ProtoNumber(3) val clientRecords: List<ClientRecordExport>,
    @ProtoNumber(4) val dashboardBusinessId: Uuid?,
) {
    @Serializable
    data class OwnedBusinessExport(
        @ProtoNumber(1) val id: Uuid,
        @ProtoNumber(2) val name: String,
        @ProtoNumber(3) val description: String,
        @ProtoNumber(4) val address: String,
        @ProtoNumber(5) val latitude: Double?,
        @ProtoNumber(6) val longitude: Double?,
        @ProtoNumber(7) val currency: String,
        @ProtoNumber(8) val timeZone: String,
        @ProtoNumber(9) val phone: String?,
        @ProtoNumber(10) val instagram: String?,
        @ProtoNumber(11) val telegram: String?,
        @ProtoNumber(12) val viber: String?,
        @ProtoNumber(13) val whatsapp: String?,
        @ProtoNumber(14) val clientDataRetentionMonths: Int,
        @ProtoNumber(15) val createdAt: Instant,
    ) {
        companion object {
            fun stub(
                id: Uuid = Uuid.random(),
                name: String = "Studio",
                description: String = "",
                address: String = "",
                latitude: Double? = null,
                longitude: Double? = null,
                currency: String = "UAH",
                timeZone: String = "Europe/Kyiv",
                phone: String? = null,
                instagram: String? = null,
                telegram: String? = null,
                viber: String? = null,
                whatsapp: String? = null,
                clientDataRetentionMonths: Int = 36,
                createdAt: Instant = Instant.fromEpochMilliseconds(0),
            ) = OwnedBusinessExport(id, name, description, address, latitude, longitude, currency, timeZone, phone, instagram, telegram, viber, whatsapp, clientDataRetentionMonths, createdAt)
        }
    }

    @Serializable
    data class EmploymentExport(
        @ProtoNumber(1) val businessId: Uuid,
        @ProtoNumber(2) val businessName: String,
        @ProtoNumber(3) val name: String,
        @ProtoNumber(4) val lastName: String,
        @ProtoNumber(5) val phone: String?,
        @ProtoNumber(6) val email: String?,
        @ProtoNumber(7) val suspendedAt: Instant?,
        @ProtoNumber(8) val permissions: List<PermissionExport>,
        @ProtoNumber(9) val workingHours: List<WorkingHoursExport>,
        @ProtoNumber(10) val daysOff: List<DayOffExport>,
    ) {
        companion object {
            fun stub(
                businessId: Uuid = Uuid.random(),
                businessName: String = "Studio",
                name: String = "Olena",
                lastName: String = "Koval",
                phone: String? = null,
                email: String? = null,
                suspendedAt: Instant? = null,
                permissions: List<PermissionExport> = emptyList(),
                workingHours: List<WorkingHoursExport> = emptyList(),
                daysOff: List<DayOffExport> = emptyList(),
            ) = EmploymentExport(businessId, businessName, name, lastName, phone, email, suspendedAt, permissions, workingHours, daysOff)
        }
    }

    @Serializable
    data class PermissionExport(
        @ProtoNumber(1) val resource: String,
        @ProtoNumber(2) val view: Boolean,
        @ProtoNumber(3) val update: Boolean,
        @ProtoNumber(4) val delete: Boolean,
    )

    @Serializable
    data class WorkingHoursExport(
        @ProtoNumber(1) val dayOfWeek: String,
        @ProtoNumber(2) val start: LocalTime,
        @ProtoNumber(3) val end: LocalTime,
    )

    @Serializable
    data class DayOffExport(
        @ProtoNumber(1) val start: LocalDate,
        @ProtoNumber(2) val end: LocalDate,
    )

    @Serializable
    data class ClientRecordExport(
        @ProtoNumber(1) val businessId: Uuid,
        @ProtoNumber(2) val businessName: String,
        @ProtoNumber(3) val name: String,
        @ProtoNumber(4) val lastName: String,
        @ProtoNumber(5) val phone: String?,
        @ProtoNumber(6) val email: String?,
        @ProtoNumber(7) val description: String?,
        @ProtoNumber(8) val lastActivityAt: Instant,
    ) {
        companion object {
            fun stub(
                businessId: Uuid = Uuid.random(),
                businessName: String = "Studio",
                name: String = "Olena",
                lastName: String = "Koval",
                phone: String? = null,
                email: String? = null,
                description: String? = null,
                lastActivityAt: Instant = Instant.fromEpochMilliseconds(0),
            ) = ClientRecordExport(businessId, businessName, name, lastName, phone, email, description, lastActivityAt)
        }
    }

    companion object {
        fun stub(
            ownedBusiness: OwnedBusinessExport? = null,
            employments: List<EmploymentExport> = emptyList(),
            clientRecords: List<ClientRecordExport> = emptyList(),
            dashboardBusinessId: Uuid? = null,
        ) = BusinessUserDataExport(ownedBusiness, employments, clientRecords, dashboardBusinessId)
    }
}
