package com.bookk.auth.domain.api.actionconfirmation.operation

import com.bookk.auth.domain.api.actionconfirmation.entity.ActionConfirmationRequest
import com.bookk.auth.domain.api.actionconfirmation.entity.ActionConfirmationToken
import kotlin.uuid.Uuid

interface ConfirmAction {
    suspend operator fun invoke(userId: Uuid, request: ActionConfirmationRequest): Result<ActionConfirmationToken>
}
