package com.bookk.auth.domain.api.identification.operation

import com.bookk.auth.domain.api.identification.entity.PasskeyList
import kotlin.uuid.Uuid

interface GetAvailablePasskeys {
    suspend operator fun invoke(authId: Uuid): Result<PasskeyList>
}
