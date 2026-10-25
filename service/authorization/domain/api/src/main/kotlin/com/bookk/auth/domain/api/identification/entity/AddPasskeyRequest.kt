package com.bookk.auth.domain.api.identification.entity

import com.bookk.auth.domain.api.authentication.entity.FinishRegistrationRequest
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
class AddPasskeyRequest(
    @ProtoNumber(1) override val requestId: String,
    @ProtoNumber(2) override val publicKeyCredentialJson: String
) : FinishRegistrationRequest