package com.bookk.appointments.domain.api.entity

import com.bookk.core.domain.entity.Page
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
class AppointmentPage(
    @ProtoNumber(1) val data: List<Appointment>,
    @ProtoNumber(3) val nextCursor: String?,
) {
    companion object {
        fun of(page: Page<Appointment>): AppointmentPage {
            return AppointmentPage(data = page.items, nextCursor = page.nextCursor)
        }
    }
}
