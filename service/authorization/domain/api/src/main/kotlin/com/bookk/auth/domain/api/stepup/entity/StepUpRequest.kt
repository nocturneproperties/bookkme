package com.bookk.auth.domain.api.stepup.entity

import com.bookk.auth.domain.api.authentication.entity.FinishAssertionRequest
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.uuid.Uuid

@Serializable
class StepUpRequest(
    @ProtoNumber(1) override val requestId: String,
    @ProtoNumber(2) override val publicKeyCredentialJson: String,
    @ProtoNumber(3) val action: StepUpAction,
    @ProtoNumber(4) val businessId: Uuid,
    @ProtoNumber(5) val targetId: Uuid,
) : FinishAssertionRequest
