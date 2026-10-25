package com.bookk.appointments.domain.api.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
enum class AppointmentRequestStatus {
    @ProtoNumber(0) PENDING,
    @ProtoNumber(1) APPROVED,
    @ProtoNumber(2) DECLINED,
    @ProtoNumber(3) CANCELLED
}