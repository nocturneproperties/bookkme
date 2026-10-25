package com.bookk.appointments.domain.api.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
enum class AppointmentCompletedBy {
    @ProtoNumber(0) SYSTEM,
    @ProtoNumber(1) USER
}
