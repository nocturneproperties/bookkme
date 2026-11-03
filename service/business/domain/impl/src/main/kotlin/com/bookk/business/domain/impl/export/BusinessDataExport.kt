package com.bookk.business.domain.impl.export

import com.bookk.business.domain.api.export.BusinessClientExport
import com.bookk.business.domain.api.export.BusinessUserDataExport.DayOffExport
import com.bookk.business.domain.api.export.BusinessUserDataExport.PermissionExport
import com.bookk.business.domain.api.export.BusinessUserDataExport.WorkingHoursExport
import com.bookk.server.appointments.client.api.export.AppointmentsBusinessDataExport
import kotlinx.serialization.Serializable
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Serializable
internal data class BusinessDataExport(
    val formatVersion: Int,
    val generatedAt: Instant,
    val businessId: Uuid,
    val business: ProfileExport,
    val serviceGroups: List<ServiceGroupExport>,
    val employees: List<EmployeeExport>,
    val pendingInvitations: List<InvitationExport>,
    val clients: List<BusinessClientExport>,
    val appointments: AppointmentsBusinessDataExport,
) {
    @Serializable
    data class ProfileExport(
        val name: String,
        val description: String,
        val address: String,
        val latitude: Double?,
        val longitude: Double?,
        val currency: String,
        val timeZone: String,
        val contacts: List<ContactExport>,
        val workingHours: List<WorkingHoursExport>,
        val daysOff: List<DayOffExport>,
        val clientDataRetentionMonths: Int,
    )

    @Serializable
    data class ContactExport(
        val kind: String,
        val value: String,
    )

    @Serializable
    data class ServiceGroupExport(
        val id: Uuid,
        val name: String,
        val services: List<ServiceExport>,
    )

    @Serializable
    data class ServiceExport(
        val id: Uuid,
        val name: String,
        val price: String,
        val durationMinutes: Long,
        val available: Boolean,
    )

    @Serializable
    data class EmployeeExport(
        val id: Uuid,
        val role: Role,
        val name: String,
        val lastName: String,
        val phone: String?,
        val email: String?,
        val joinedAt: Instant,
        val suspendedAt: Instant?,
        val serviceIds: List<Uuid>,
        val workingHours: List<WorkingHoursExport>,
        val daysOff: List<DayOffExport>,
        val permissions: List<PermissionExport>,
    )

    @Serializable
    data class InvitationExport(
        val id: Uuid,
        val invitedByEmployeeId: Uuid?,
        val createdAt: Instant,
    )

    @Serializable
    enum class Role {
        OWNER,
        EMPLOYEE,
    }

    companion object {
        const val FORMAT_VERSION = 1
    }
}
