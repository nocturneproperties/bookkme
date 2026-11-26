package com.bookk.auth.domain.impl.operation.actionconfirmation

import com.bookk.auth.domain.api.actionconfirmation.entity.ActionConfirmationRequest
import com.bookk.auth.domain.api.actionconfirmation.entity.ActionConfirmationToken
import com.bookk.auth.domain.api.actionconfirmation.operation.ConfirmAction
import com.bookk.auth.domain.api.authentication.operation.FinishAssertion
import com.bookk.auth.domain.api.authentication.operation.finishOwnedBy
import com.bookk.core.domain.entity.runSuspendCatching
import library.signing.TokenIssuer
import kotlin.uuid.Uuid

internal class ConfirmActionImpl(
    private val finishAssertion: FinishAssertion,
    private val tokenIssuer: TokenIssuer,
) : ConfirmAction {
    override suspend fun invoke(
        userId: Uuid,
        request: ActionConfirmationRequest,
    ): Result<ActionConfirmationToken> = runSuspendCatching {
        finishAssertion.finishOwnedBy(userId, request).getOrThrow()
        tokenIssuer.issueActionConfirmation(userId, request.action, request.businessId, request.targetId)
    }
}
