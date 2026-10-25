package com.bookk.user.domain.api.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
class UserExistInfo(
    @ProtoNumber(1) val exists: Boolean
)