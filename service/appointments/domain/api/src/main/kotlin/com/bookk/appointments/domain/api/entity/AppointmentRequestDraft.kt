package com.bookk.appointments.domain.api.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Serializable
data class AppointmentRequestDraft(
    @ProtoNumber(1) val businessId: Uuid,
    @ProtoNumber(2) val employeeId: Uuid,
    @ProtoNumber(3) val services: List<RequestedService>,
    @ProtoNumber(4) val date: Instant,
    @ProtoNumber(5) val note: String,
    @ProtoNumber(6) val offerToken: String
) {
    companion object {
        fun stub(
            businessId: Uuid = Uuid.random(),
            employeeId: Uuid = Uuid.random(),
            services: List<RequestedService> = listOf(RequestedService.stub()),
            date: Instant = Instant.fromEpochMilliseconds(0),
            offerToken: String = "token"
        ) = AppointmentRequestDraft(
            businessId = businessId,
            employeeId = employeeId,
            services = services,
            date = date,
            note = "Note",
            offerToken = offerToken
        )
    }
}
