package com.bookk.appointments.domain.api.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.uuid.Uuid

@Serializable
data class ClientSnapshot(
    @ProtoNumber(1) val id: Uuid,
    @ProtoNumber(2) val fullName: String,
    @ProtoNumber(3) val phone: String?,
    @ProtoNumber(4) val email: String?
) {
    companion object {
        fun stub() = ClientSnapshot(
            id = Uuid.random(),
            fullName = "Client Name",
            phone = "123456789",
            email = "client@example.com"
        )
    }
}