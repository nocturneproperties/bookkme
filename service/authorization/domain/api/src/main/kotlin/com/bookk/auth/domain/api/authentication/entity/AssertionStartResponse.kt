package com.bookk.auth.domain.api.authentication.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class AssertionStartResponse(
    @ProtoNumber(1) val requestId: String,
    @ProtoNumber(2) val challengeJson: String,
    @ProtoNumber(3) val challenge: String
)