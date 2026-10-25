package com.bookk.auth.domain.api.identification.operation

import com.bookk.auth.domain.api.identification.entity.PasskeyResponse
import kotlin.uuid.Uuid

interface GetAvailablePasskeys {
    suspend operator fun invoke(authId: Uuid): Result<List<PasskeyResponse>>
}