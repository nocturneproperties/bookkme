package com.bookk.auth.domain.api.demo.operation

import com.bookk.auth.domain.api.demo.entity.DemoCaller
import com.bookk.auth.domain.api.demo.entity.DemoUpgradeFinishRequest
import com.bookk.auth.domain.api.token.entity.AuthTokens

interface FinishDemoUpgrade {
    suspend operator fun invoke(caller: DemoCaller, request: DemoUpgradeFinishRequest): Result<AuthTokens>
}
