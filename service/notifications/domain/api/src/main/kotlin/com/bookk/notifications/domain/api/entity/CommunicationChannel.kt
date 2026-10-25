package com.bookk.notifications.domain.api.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
enum class CommunicationChannel {
    @ProtoNumber(0) TELEGRAM,
    @ProtoNumber(1) EMAIL,
    @ProtoNumber(2) PUSH_NOTIFICATIONS,
}
