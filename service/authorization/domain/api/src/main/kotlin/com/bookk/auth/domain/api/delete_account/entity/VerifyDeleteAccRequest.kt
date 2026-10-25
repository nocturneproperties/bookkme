package com.bookk.auth.domain.api.delete_account.entity

import com.bookk.auth.domain.api.authentication.entity.FinishAssertionRequest
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
class VerifyDeleteAccRequest(
    @ProtoNumber(1) override val requestId: String,
    @ProtoNumber(2) override val publicKeyCredentialJson: String,
) : FinishAssertionRequest