package com.bookk.auth.domain.api.stepup.operation

import com.bookk.auth.domain.api.stepup.entity.StepUpRequest
import com.bookk.auth.domain.api.stepup.entity.StepUpToken
import kotlin.uuid.Uuid

interface IssueStepUpToken {
    suspend operator fun invoke(userId: Uuid, request: StepUpRequest): Result<StepUpToken>
}
