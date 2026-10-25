package com.bookk.appointments.domain.api.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
enum class AppointmentStatus {
    @ProtoNumber(0) SCHEDULED,
    @ProtoNumber(1) COMPLETED,
    @ProtoNumber(2) CANCELLED,
    @ProtoNumber(3) NO_SHOW
}