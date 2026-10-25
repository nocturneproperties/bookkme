package com.bookk.server.user.client.api

import com.bookk.user.domain.api.entity.User
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.uuid.Uuid

@Serializable
class UserSnapshot(
    @ProtoNumber(1) val id: Uuid,
    @ProtoNumber(2) val name: String,
    @ProtoNumber(3) val lastName: String,
    @ProtoNumber(4) val email: String,
    @ProtoNumber(5) val phone: String?
) {
    companion object {
        internal fun fromUser(user: User): UserSnapshot {
            return UserSnapshot(
                id = user.id,
                name = user.name,
                lastName = user.lastName,
                email = user.email,
                phone = user.phone
            )
        }

        fun stub(
            id: Uuid = Uuid.random(),
            name: String = "Alice",
            lastName: String = "Smith",
            email: String = "user@example.com",
            phone: String? = null
        ) = UserSnapshot(id = id, name = name, lastName = lastName, email = email, phone = phone)
    }
}