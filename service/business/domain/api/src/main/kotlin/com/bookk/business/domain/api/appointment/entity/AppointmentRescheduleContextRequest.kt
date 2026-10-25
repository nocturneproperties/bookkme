package com.bookk.business.domain.api.appointment.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.uuid.Uuid

@Serializable
data class AppointmentRescheduleContextRequest(
    @ProtoNumber(1) val employeeId: Uuid?,
    @ProtoNumber(2) val serviceIds: List<Uuid>
) {
    companion object {
        fun stub(
            employeeId: Uuid? = Uuid.random(),
            serviceIds: List<Uuid> = listOf(Uuid.random())
        ) = AppointmentRescheduleContextRequest(employeeId = employeeId, serviceIds = serviceIds)
    }
}
