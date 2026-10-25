package com.bookk.appointments.domain.api.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.uuid.Uuid

@Serializable
data class EmployeeSnapshot(
    @ProtoNumber(1) val id: Uuid,
    @ProtoNumber(2) val userId: Uuid,
    @ProtoNumber(3) val fullName: String
) {
    companion object {
        fun stub(id: Uuid = Uuid.random(), userId: Uuid = Uuid.random()): EmployeeSnapshot {
            return EmployeeSnapshot(id = id, userId = userId, fullName = "Example name")
        }
    }
}
