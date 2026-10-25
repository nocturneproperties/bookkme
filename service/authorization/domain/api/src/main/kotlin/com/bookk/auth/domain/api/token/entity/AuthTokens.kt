package com.bookk.auth.domain.api.token.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class AuthTokens(
    @ProtoNumber(1) val accessToken: String,
    @ProtoNumber(2) val refreshToken: String
)