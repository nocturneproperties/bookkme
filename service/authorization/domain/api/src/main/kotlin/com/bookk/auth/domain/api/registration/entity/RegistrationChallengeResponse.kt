package com.bookk.auth.domain.api.registration.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.uuid.Uuid

@Serializable
data class RegistrationChallengeResponse(
    @ProtoNumber(1) val requestId: String,
    @ProtoNumber(2) val challenge: String,
    @ProtoNumber(3) val challengeJson: String,
    @ProtoNumber(4) val userHandle: String,
    @ProtoNumber(5) val displayName: String,
) {
    companion object {
        fun stub(
            requestId: String = Uuid.random().toString(),
            challenge: String = "challenge",
            challengeJson: String = "{}",
            userHandle: String = "handle",
            displayName: String = "John Doe",
        ) = RegistrationChallengeResponse(requestId, challenge, challengeJson, userHandle, displayName)
    }
}
