package com.bookk.auth.domain.impl.operation.stepup

import com.bookk.auth.domain.api.authentication.operation.FinishAssertion
import com.bookk.auth.domain.api.authentication.operation.finishOwnedBy
import com.bookk.auth.domain.api.stepup.entity.StepUpAction
import com.bookk.auth.domain.api.stepup.entity.StepUpRequest
import com.bookk.auth.domain.api.stepup.entity.StepUpToken
import com.bookk.auth.domain.api.stepup.operation.IssueStepUpToken
import com.bookk.core.domain.entity.runSuspendCatching
import com.bookk.server.auth.client.StepUpClaims
import library.signing.TokenAudience
import library.signing.TokenIssuer
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

internal class IssueStepUpTokenImpl(
    private val finishAssertion: FinishAssertion,
    private val tokenIssuer: TokenIssuer
) : IssueStepUpToken {
    override suspend fun invoke(userId: Uuid, request: StepUpRequest): Result<StepUpToken> = runSuspendCatching {
        finishAssertion.finishOwnedBy(userId, request).getOrThrow()
        val token = tokenIssuer.issue(STEP_UP_TTL) {
            withAudience(TokenAudience.stepUp)
                .withJWTId(Uuid.random().toString())
                .withClaim(StepUpClaims.CLAIM_USER_ID, userId.toString())
                .withClaim(StepUpClaims.CLAIM_ACTION, request.action.claim)
                .withClaim(StepUpClaims.CLAIM_BUSINESS_ID, request.businessId.toString())
                .withClaim(StepUpClaims.CLAIM_TARGET_ID, request.targetId.toString())
        }
        StepUpToken(token)
    }

    private val StepUpAction.claim: String
        get() = when (this) {
            StepUpAction.DELETE_CLIENT -> StepUpClaims.ACTION_DELETE_CLIENT
        }

    companion object {
        val STEP_UP_TTL = 2.minutes
    }
}
