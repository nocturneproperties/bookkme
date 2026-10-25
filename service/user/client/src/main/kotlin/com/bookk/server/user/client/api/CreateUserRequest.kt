package com.bookk.server.user.client.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
class CreateUserRequest(
    @ProtoNumber(1) val name: String,
    @ProtoNumber(2) val lastName: String,
    @ProtoNumber(3) val email: String
)