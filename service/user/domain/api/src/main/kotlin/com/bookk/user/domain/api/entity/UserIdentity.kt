package com.bookk.user.domain.api.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
class UserIdentity(
    @ProtoNumber(1) val phone: String,
    @ProtoNumber(2) val email: String
)