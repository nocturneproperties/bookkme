package com.bookk.user.domain.api.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class TelegramUsernameBody(
    @ProtoNumber(1) val username: String?,
)
