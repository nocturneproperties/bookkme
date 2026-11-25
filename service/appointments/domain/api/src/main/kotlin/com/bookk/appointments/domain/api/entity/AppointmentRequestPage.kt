package com.bookk.appointments.domain.api.entity

import com.bookk.core.domain.entity.Page
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
class AppointmentRequestPage(
    @ProtoNumber(1) val data: List<AppointmentRequest>,
    @ProtoNumber(2) val nextCursor: String?,
) {
    companion object {
        fun of(page: Page<AppointmentRequest>): AppointmentRequestPage {
            return AppointmentRequestPage(data = page.items, nextCursor = page.nextCursor)
        }
    }
}
