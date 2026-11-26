package com.bookk.core.domain.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
enum class DemoBusinessKind {
    @ProtoNumber(0)
    GENERAL,

    @ProtoNumber(1)
    BEAUTY,

    @ProtoNumber(2)
    FITNESS,

    @ProtoNumber(3)
    TUTORING,

    @ProtoNumber(4)
    HEALTH,
}
