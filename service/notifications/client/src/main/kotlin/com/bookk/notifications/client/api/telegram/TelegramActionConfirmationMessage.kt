package com.bookk.notifications.client.api.telegram

import com.bookk.core.domain.entity.Language
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class TelegramActionConfirmationMessage(
    @ProtoNumber(1) val action: TelegramConfirmedAction,
    @ProtoNumber(2) val startParameter: String,
    @ProtoNumber(3) val language: Language,
    @ProtoNumber(4) val challengeId: String = "",
) {
    companion object {
        fun stub(
            action: TelegramConfirmedAction = TelegramConfirmedAction.DELETE_ACCOUNT,
            startParameter: String = "op_delete-account_0123456789abcdef0123456789abcdef",
            language: Language = Language.EN,
            challengeId: String = "0123456789abcdef0123456789abcdef",
        ): TelegramActionConfirmationMessage {
            return TelegramActionConfirmationMessage(action, startParameter, language, challengeId)
        }
    }
}
