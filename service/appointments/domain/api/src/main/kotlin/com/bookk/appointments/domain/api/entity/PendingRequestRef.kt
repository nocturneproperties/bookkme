package com.bookk.appointments.domain.api.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.uuid.Uuid

@Serializable
data class PendingRequestRef(
    @ProtoNumber(1) val id: Uuid,
    @ProtoNumber(2) val employeeUserId: Uuid,
) {
    companion object {
        fun stub(id: Uuid = Uuid.random(), employeeUserId: Uuid = Uuid.random()): PendingRequestRef {
            return PendingRequestRef(id = id, employeeUserId = employeeUserId)
        }
    }
}
