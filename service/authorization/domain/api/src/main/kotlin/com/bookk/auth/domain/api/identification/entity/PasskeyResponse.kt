package com.bookk.auth.domain.api.identification.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.time.Instant
import kotlin.uuid.Uuid

@Serializable
class PasskeyResponse(
    @ProtoNumber(1) val id: Uuid,
    @ProtoNumber(2) val name: String,
    @ProtoNumber(3) val createdAt: Instant,
    @ProtoNumber(4) val lastUsedAt: Instant,
    @ProtoNumber(5) val isBackedUp: Boolean
)