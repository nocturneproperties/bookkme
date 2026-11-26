package com.bookk.notifications.domain.api.entity

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class PendingTelegramConfirmation(
    @ProtoNumber(1) val challengeId: String,
    @ProtoNumber(2) val messageId: Long,
    @ProtoNumber(3) val confirmedText: String,
) {
    companion object {
        fun stub(
            challengeId: String = "0123456789abcdef0123456789abcdef",
            messageId: Long = 1,
            confirmedText: String = "Confirmed",
        ): PendingTelegramConfirmation {
            return PendingTelegramConfirmation(challengeId, messageId, confirmedText)
        }
    }
}
