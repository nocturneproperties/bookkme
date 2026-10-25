package com.bookk.appointments.domain.api.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.uuid.Uuid

@Serializable
data class AppointmentCancellation(
    @ProtoNumber(1) val id: Uuid,
    @ProtoNumber(3) val reason: String
) {
    companion object {
        fun stub(id: Uuid = Uuid.random()) = AppointmentCancellation(
            id = id,
            reason = "Test reason"
        )
    }
}
