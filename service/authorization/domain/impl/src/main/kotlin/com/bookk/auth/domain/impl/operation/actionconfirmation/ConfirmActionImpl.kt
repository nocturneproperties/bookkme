package com.bookk.auth.domain.impl.operation.actionconfirmation

import com.bookk.auth.domain.api.actionconfirmation.entity.ActionConfirmationRequest
import com.bookk.auth.domain.api.actionconfirmation.entity.ActionConfirmationToken
import com.bookk.auth.domain.api.actionconfirmation.entity.ConfirmableAction
import com.bookk.auth.domain.api.actionconfirmation.operation.ConfirmAction
import com.bookk.auth.domain.api.authentication.operation.FinishAssertion
import com.bookk.auth.domain.api.authentication.operation.finishOwnedBy
import com.bookk.core.domain.entity.runSuspendCatching
import library.actionconfirmation.ActionConfirmationClaims
import library.signing.TokenAudience
import library.signing.TokenIssuer
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

internal class ConfirmActionImpl(
    private val finishAssertion: FinishAssertion,
    private val tokenIssuer: TokenIssuer,
) : ConfirmAction {
    override suspend fun invoke(userId: Uuid, request: ActionConfirmationRequest): Result<ActionConfirmationToken> = runSuspendCatching {
        finishAssertion.finishOwnedBy(userId, request).getOrThrow()
        val token = tokenIssuer.issue(ACTION_CONFIRMATION_TTL) {
            withAudience(TokenAudience.actionConfirmation)
                .withJWTId(Uuid.random().toString())
                .withClaim(ActionConfirmationClaims.CLAIM_USER_ID, userId.toString())
                .withClaim(ActionConfirmationClaims.CLAIM_ACTION, request.action.claim)
                .withClaim(ActionConfirmationClaims.CLAIM_BUSINESS_ID, request.businessId.toString())
                .withClaim(ActionConfirmationClaims.CLAIM_TARGET_ID, request.targetId.toString())
        }
        ActionConfirmationToken(token)
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
