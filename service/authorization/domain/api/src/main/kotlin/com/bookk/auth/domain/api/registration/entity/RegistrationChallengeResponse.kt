package com.bookk.auth.domain.api.registration.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class RegistrationChallengeResponse(
    @ProtoNumber(1) val requestId: String,
    @ProtoNumber(2) val challenge: String,
    @ProtoNumber(3) val challengeJson: String,
    @ProtoNumber(4) val userHandle: String,
    @ProtoNumber(5) val displayName: String,
)