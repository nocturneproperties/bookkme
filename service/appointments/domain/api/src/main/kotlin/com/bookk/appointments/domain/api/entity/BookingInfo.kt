package com.bookk.appointments.domain.api.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.uuid.Uuid

@Serializable
data class BookingInfo(
    @ProtoNumber(1) val businessId: Uuid,
    @ProtoNumber(2) val appointmentNote: String,
) {
    companion object {
        fun stub(businessId: Uuid = Uuid.random(), appointmentNote: String = "Note"): BookingInfo {
            return BookingInfo(businessId = businessId, appointmentNote = appointmentNote)
        }
    }
}
