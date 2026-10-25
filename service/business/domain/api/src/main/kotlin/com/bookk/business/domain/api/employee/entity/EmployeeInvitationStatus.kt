package com.bookk.business.domain.api.employee.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
enum class EmployeeInvitationStatus {
    @ProtoNumber(0) PENDING,
    @ProtoNumber(1) REDEEMED,
    @ProtoNumber(2) EXPIRED,
    @ProtoNumber(3) REVOKED
}
