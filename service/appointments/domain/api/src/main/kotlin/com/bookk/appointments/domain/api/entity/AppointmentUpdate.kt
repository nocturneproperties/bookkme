package com.bookk.appointments.domain.api.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Serializable
data class AppointmentUpdate(
    @ProtoNumber(1) val id: Uuid,
    @ProtoNumber(8) val date: Instant,
    @ProtoNumber(9) val note: String,
    @ProtoNumber(13) val employeeId: Uuid,
    @ProtoNumber(14) val services: List<RequestedService>
) {
    companion object {
        fun stub(
            id: Uuid = Uuid.random(),
            date: Instant = Instant.fromEpochMilliseconds(0),
            employeeId: Uuid = Uuid.random(),
            services: List<RequestedService> = listOf(RequestedService.stub())
        ) = AppointmentUpdate(id = id, date = date, note = "Note", employeeId = employeeId, services = services)
    }
}
