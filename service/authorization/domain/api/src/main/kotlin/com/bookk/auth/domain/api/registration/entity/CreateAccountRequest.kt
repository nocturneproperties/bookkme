package com.bookk.auth.domain.api.registration.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class CreateAccountRequest(
    @ProtoNumber(1) val name: String,
    @ProtoNumber(3) val email: String,
)
