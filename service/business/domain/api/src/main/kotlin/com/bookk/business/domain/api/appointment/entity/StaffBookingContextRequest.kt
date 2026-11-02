package com.bookk.business.domain.api.appointment.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.uuid.Uuid

@Serializable
data class StaffBookingContextRequest(
    @ProtoNumber(1) val employeeId: Uuid,
    @ProtoNumber(2) val clientId: Uuid,
    @ProtoNumber(3) val serviceIds: List<Uuid>,
) {
    companion object {
        fun stub(
            employeeId: Uuid = Uuid.random(),
            clientId: Uuid = Uuid.random(),
            serviceIds: List<Uuid> = listOf(Uuid.random()),
        ) = StaffBookingContextRequest(employeeId = employeeId, clientId = clientId, serviceIds = serviceIds)
    }
}
