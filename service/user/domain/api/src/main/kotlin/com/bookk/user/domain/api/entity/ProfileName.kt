package com.bookk.user.domain.api.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class ProfileName(
    @ProtoNumber(1) val name: String,
    @ProtoNumber(2) val lastName: String,
) {
    companion object {
        fun stub(name: String = "Jane", lastName: String = "Smith"): ProfileName {
            return ProfileName(name, lastName)
        }
    }
}
