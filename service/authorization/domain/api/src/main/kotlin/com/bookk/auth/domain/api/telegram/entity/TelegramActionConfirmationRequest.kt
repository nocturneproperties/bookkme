package com.bookk.auth.domain.api.telegram.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class TelegramActionConfirmationRequest(
    @ProtoNumber(1) val initData: String,
) {
    companion object {
        fun stub(
            initData: String = "auth_date=0&hash=00&signature=AA&user=%7B%22id%22%3A1%7D",
        ): TelegramActionConfirmationRequest {
            return TelegramActionConfirmationRequest(initData)
        }
    }
}
