package com.bookk.appointments.domain.api.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.uuid.Uuid

@Serializable
data class RequestClientSnapshot(
    @ProtoNumber(1) val id: Uuid,
    @ProtoNumber(2) val fullName: String,
    @ProtoNumber(3) val phone: String?,
    @ProtoNumber(4) val email: String?,
    @ProtoNumber(5) val userId: Uuid,
) {
    fun toClientSnapshot(): ClientSnapshot {
        return ClientSnapshot(id = id, fullName = fullName, phone = phone, email = email, userId = userId)
    }

    companion object {
        fun stub(userId: Uuid = Uuid.random()): RequestClientSnapshot {
            return RequestClientSnapshot(
                id = Uuid.random(),
                fullName = "Client Name",
                phone = "123456789",
                email = "client@example.com",
                userId = userId,
            )
        }
    }
}
