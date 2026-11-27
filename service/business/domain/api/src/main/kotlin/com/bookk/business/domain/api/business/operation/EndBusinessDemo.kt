package com.bookk.business.domain.api.business.operation

import kotlin.uuid.Uuid

interface EndBusinessDemo {
    suspend operator fun invoke(ownerUserId: Uuid): Result<Unit>
}
