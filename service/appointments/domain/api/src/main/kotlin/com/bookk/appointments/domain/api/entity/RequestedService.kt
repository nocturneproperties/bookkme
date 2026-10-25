package com.bookk.appointments.domain.api.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.uuid.Uuid

@Serializable
data class RequestedService(
    @ProtoNumber(1) val serviceId: Uuid,
    @ProtoNumber(2) val count: Int
) {
    companion object {
        fun stub(
            serviceId: Uuid = Uuid.random(),
            count: Int = 1
        ) = RequestedService(serviceId = serviceId, count = count)
    }
}
