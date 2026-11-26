package com.bookk.auth.domain.impl.operation.actionconfirmation

import com.bookk.auth.domain.api.actionconfirmation.entity.ActionConfirmationToken
import com.bookk.auth.domain.api.actionconfirmation.entity.ConfirmableAction
import library.actionconfirmation.ActionConfirmationClaims
import library.signing.TokenAudience
import library.signing.TokenIssuer
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

internal class ActionConfirmationTokenIssuer(
    private val tokenIssuer: TokenIssuer,
) {
    suspend fun issue(
        userId: Uuid,
        action: ConfirmableAction,
        businessId: Uuid,
        targetId: Uuid,
    ): ActionConfirmationToken {
        val token = tokenIssuer.issue(ACTION_CONFIRMATION_TTL) {
            withAudience(TokenAudience.actionConfirmation)
                .withJWTId(Uuid.random().toString())
                .withClaim(ActionConfirmationClaims.CLAIM_USER_ID, userId.toString())
                .withClaim(ActionConfirmationClaims.CLAIM_ACTION, action.claim)
                .withClaim(ActionConfirmationClaims.CLAIM_BUSINESS_ID, businessId.toString())
                .withClaim(ActionConfirmationClaims.CLAIM_TARGET_ID, targetId.toString())
        }
        return ActionConfirmationToken(token)
    }

    private val ConfirmableAction.claim: String
        get() = when (this) {
            ConfirmableAction.DELETE_CLIENT -> ActionConfirmationClaims.ACTION_DELETE_CLIENT
            ConfirmableAction.EXPORT_PERSONAL_DATA -> ActionConfirmationClaims.ACTION_EXPORT_PERSONAL_DATA
            ConfirmableAction.EXPORT_BUSINESS_DATA -> ActionConfirmationClaims.ACTION_EXPORT_BUSINESS_DATA
            ConfirmableAction.DELETE_ACCOUNT -> ActionConfirmationClaims.ACTION_DELETE_ACCOUNT
        }

    companion object {
        val ACTION_CONFIRMATION_TTL = 2.minutes
    }
}
