package com.bookk.business.domain.api.appointment.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.uuid.Uuid

@Serializable
data class AppointmentBookingContextRequest(
    @ProtoNumber(1) val employeeId: Uuid,
    @ProtoNumber(2) val userId: Uuid,
    @ProtoNumber(3) val serviceIds: List<Uuid>
) {
    companion object {
        fun stub(
            employeeId: Uuid = Uuid.random(),
            userId: Uuid = Uuid.random(),
            serviceIds: List<Uuid> = listOf(Uuid.random())
        ) = AppointmentBookingContextRequest(employeeId = employeeId, userId = userId, serviceIds = serviceIds)
    }
}
