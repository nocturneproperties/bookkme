package com.bookk.business.domain.api.employee.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Serializable
data class EmployeeInvitation(
    @ProtoNumber(1) val id: Uuid,
    @ProtoNumber(2) val businessId: Uuid,
    @ProtoNumber(3) val invitedBy: Uuid,
    @ProtoNumber(4) val code: String?,
    @ProtoNumber(5) val status: EmployeeInvitationStatus,
    @ProtoNumber(6) val createdAt: Instant
) {
    companion object {
        fun stub(
            id: Uuid = Uuid.random(),
            businessId: Uuid = Uuid.random(),
            invitedBy: Uuid = Uuid.random(),
            code: String? = "STUBCODE",
            status: EmployeeInvitationStatus = EmployeeInvitationStatus.PENDING,
            createdAt: Instant = Instant.fromEpochMilliseconds(0)
        ) = EmployeeInvitation(
            id = id,
            businessId = businessId,
            invitedBy = invitedBy,
            code = code,
            status = status,
            createdAt = createdAt
        )
    }
}
