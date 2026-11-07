package com.bookk.auth.domain.api.actionconfirmation.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class ActionConfirmationToken(
    @ProtoNumber(1) val token: String,
)
