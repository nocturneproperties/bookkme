package com.bookk.auth.domain.api.telegram.entity

import com.bookk.auth.domain.api.actionconfirmation.entity.ConfirmableAction
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.uuid.Uuid

@Serializable
data class TelegramConfirmationChallenge(
    @ProtoNumber(1) val userId: Uuid,
    @ProtoNumber(2) val action: ConfirmableAction,
    @ProtoNumber(3) val businessId: Uuid,
    @ProtoNumber(4) val targetId: Uuid,
) {
    companion object {
        fun stub(
            userId: Uuid = Uuid.random(),
            action: ConfirmableAction = ConfirmableAction.DELETE_ACCOUNT,
            businessId: Uuid = Uuid.NIL,
            targetId: Uuid = userId,
        ): TelegramConfirmationChallenge {
            return TelegramConfirmationChallenge(userId, action, businessId, targetId)
        }
    }
}
