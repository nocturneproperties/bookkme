package com.bookk.notifications.client.api.telegram

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
enum class TelegramConfirmedAction {
    @ProtoNumber(0)
    DELETE_ACCOUNT,

    @ProtoNumber(1)
    EXPORT_PERSONAL_DATA,
}
