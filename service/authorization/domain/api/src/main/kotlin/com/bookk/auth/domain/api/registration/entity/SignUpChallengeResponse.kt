package com.bookk.auth.domain.api.registration.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import library.email.EmailVerificationStarted

@Serializable
data class SignUpChallengeResponse(
    @ProtoNumber(1) val requestId: String,
    @ProtoNumber(2) val challenge: String,
    @ProtoNumber(3) val challengeJson: String,
    @ProtoNumber(4) val userHandle: String,
    @ProtoNumber(5) val displayName: String,
    @ProtoNumber(6) val emailVerification: EmailVerificationStarted,
) {
    companion object {
        fun of(passkey: RegistrationChallengeResponse, emailVerification: EmailVerificationStarted) = SignUpChallengeResponse(
            requestId = passkey.requestId,
            challenge = passkey.challenge,
            challengeJson = passkey.challengeJson,
            userHandle = passkey.userHandle,
            displayName = passkey.displayName,
            emailVerification = emailVerification,
        )

        fun stub(
            passkey: RegistrationChallengeResponse = RegistrationChallengeResponse.stub(),
            emailVerification: EmailVerificationStarted = EmailVerificationStarted.stub(),
        ) = of(passkey, emailVerification)
    }
}
