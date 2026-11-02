package com.bookk.auth.domain.api.stepup.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class StepUpToken(
    @ProtoNumber(1) val token: String
)
