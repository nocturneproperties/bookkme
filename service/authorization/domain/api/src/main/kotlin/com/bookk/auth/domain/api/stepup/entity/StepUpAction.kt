package com.bookk.auth.domain.api.stepup.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
enum class StepUpAction {
    @ProtoNumber(0) DELETE_CLIENT
}
