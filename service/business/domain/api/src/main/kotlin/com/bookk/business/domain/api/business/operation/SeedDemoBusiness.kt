package com.bookk.business.domain.api.business.operation

import com.bookk.core.domain.entity.DemoBusinessKind
import com.bookk.core.domain.entity.Language
import kotlin.uuid.Uuid

interface SeedDemoBusiness {
    suspend operator fun invoke(
        ownerUserId: Uuid,
        timeZoneId: String,
        language: Language,
        businessKind: DemoBusinessKind,
    ): Result<Unit>
}
