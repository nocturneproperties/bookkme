package com.bookk.auth.domain.api.authentication.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
class EditEmailRequest(
    @ProtoNumber(1) override val requestId: String,
    @ProtoNumber(2) override val publicKeyCredentialJson: String,
    @ProtoNumber(3) val newEmail: String
) : FinishAssertionRequest