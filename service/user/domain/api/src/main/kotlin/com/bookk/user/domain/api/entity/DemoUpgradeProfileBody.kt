package com.bookk.user.domain.api.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class DemoUpgradeProfileBody(
    @ProtoNumber(1) val email: String,
    @ProtoNumber(2) val name: ProfileName,
)
