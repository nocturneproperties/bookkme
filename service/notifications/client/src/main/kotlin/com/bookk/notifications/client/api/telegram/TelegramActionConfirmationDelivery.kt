package com.bookk.notifications.client.api.telegram

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class TelegramActionConfirmationDelivery(
    @ProtoNumber(1) val outcome: Outcome,
) {
    @Serializable
    enum class Outcome {
        @ProtoNumber(0)
        SENT,

        @ProtoNumber(1)
        CHAT_UNREACHABLE,

        @ProtoNumber(2)
        NO_CHAT,

        @ProtoNumber(3)
        UNAVAILABLE,

        @ProtoNumber(4)
        FAILED,
    }

    companion object {
        fun stub(outcome: Outcome = Outcome.SENT): TelegramActionConfirmationDelivery {
            return TelegramActionConfirmationDelivery(outcome)
        }
    }
}
