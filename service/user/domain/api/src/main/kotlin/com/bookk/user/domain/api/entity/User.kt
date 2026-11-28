package com.bookk.user.domain.api.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.uuid.Uuid

@Serializable
data class User(
    @ProtoNumber(1) val id: Uuid,
    @ProtoNumber(2) val name: String,
    @ProtoNumber(3) val lastName: String,
    @ProtoNumber(4) val email: String,
    @ProtoNumber(5) val phone: String?,
    @ProtoNumber(6) val telegramUserId: Long?,
    @ProtoNumber(7) val telegramUsername: String?,
) {
    companion object {
        fun stub(
            id: Uuid = Uuid.random(),
            name: String = "Alice",
            lastName: String = "Smith",
            email: String = "user@example.com",
            phone: String? = null,
            telegramUserId: Long? = null,
            telegramUsername: String? = null,
        ): User {
            return User(id, name, lastName, email, phone, telegramUserId, telegramUsername)
        }
    }
}
