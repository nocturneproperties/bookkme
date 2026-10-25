package com.bookk.business.domain.api.employee.entity

import com.bookk.business.domain.api.business.entity.BusinessPermissions
import com.bookk.business.domain.api.service.entity.Service
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import library.schedule.Schedule
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Serializable
data class Employee(
    @ProtoNumber(1) val id: Uuid,
    @ProtoNumber(2) val businessId: Uuid,
    @ProtoNumber(3) val name: String,
    @ProtoNumber(4) val lastName: String,
    @ProtoNumber(5) val phone: String?,
    @ProtoNumber(6) val email: String?,
    @ProtoNumber(7) val userId: Uuid,
    @ProtoNumber(8) val services: List<Service>,
    @ProtoNumber(9) val schedule: Schedule,
    @ProtoNumber(10) val createdAt: Instant,
    @ProtoNumber(11) val permissions: BusinessPermissions,
    @ProtoNumber(12) val suspendedAt: Instant?
) {
    val isSuspended: Boolean get() = suspendedAt != null

    companion object {
        fun stub(
            id: Uuid = Uuid.random(),
            businessId: Uuid = Uuid.random(),
            name: String = "stub-name",
            lastName: String = "stub-lastname",
            phone: String? = "+10000000000",
            email: String? = "stub@employee.com",
            userId: Uuid = Uuid.random(),
            services: List<Service> = emptyList(),
            schedule: Schedule = Schedule.empty(),
            createdAt: Instant = Instant.fromEpochMilliseconds(0),
            permissions: BusinessPermissions = BusinessPermissions.NONE,
            suspendedAt: Instant? = null
        ) = Employee(
            id = id,
            businessId = businessId,
            name = name,
            lastName = lastName,
            phone = phone,
            email = email,
            userId = userId,
            services = services,
            schedule = schedule,
            createdAt = createdAt,
            permissions = permissions,
            suspendedAt = suspendedAt
        )
    }
}
