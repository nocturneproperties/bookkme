package com.bookk.business.domain.api.user.operation

import kotlin.uuid.Uuid

interface AnonymizeUserProfile {
    suspend operator fun invoke(userId: Uuid): Result<Unit>
}
