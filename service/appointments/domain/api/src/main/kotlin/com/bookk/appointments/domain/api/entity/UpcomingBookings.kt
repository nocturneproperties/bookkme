package com.bookk.appointments.domain.api.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class UpcomingBookings(
    @ProtoNumber(1) val appointments: List<Appointment>,
    @ProtoNumber(2) val requests: List<AppointmentRequest>,
) {
    companion object {
        fun stub(
            appointments: List<Appointment> = listOf(Appointment.stub()),
            requests: List<AppointmentRequest> = listOf(AppointmentRequest.stub()),
        ): UpcomingBookings {
            return UpcomingBookings(appointments = appointments, requests = requests)
        }
    }
}
