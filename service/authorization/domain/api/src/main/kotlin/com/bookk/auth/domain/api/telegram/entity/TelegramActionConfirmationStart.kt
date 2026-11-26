package com.bookk.auth.domain.api.telegram.entity

import com.bookk.auth.domain.api.actionconfirmation.entity.ConfirmableAction
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.uuid.Uuid

@Serializable
data class TelegramActionConfirmationStart(
    @ProtoNumber(1) val action: ConfirmableAction,
    @ProtoNumber(2) val businessId: Uuid,
    @ProtoNumber(3) val targetId: Uuid,
) {
    companion object {
        fun stub(
            action: ConfirmableAction = ConfirmableAction.DELETE_ACCOUNT,
            businessId: Uuid = Uuid.NIL,
            targetId: Uuid = Uuid.random(),
        ): TelegramActionConfirmationStart {
            return TelegramActionConfirmationStart(action, businessId, targetId)
        }
    }
}
