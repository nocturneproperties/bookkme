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
    @ProtoNumber(6) val erased: Boolean = false,
) {
    fun toClientSnapshot(): ClientSnapshot {
        return ClientSnapshot(
            id = id,
            fullName = fullName,
            phone = phone,
            email = email,
            userId = userId,
            erased = erased,
        )
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
