package com.bookk.auth.domain.api.authentication.operation

import com.bookk.auth.domain.api.authentication.entity.AssertionStartResponse

interface StartAssertion {
    suspend operator fun invoke(): Result<AssertionStartResponse>
}