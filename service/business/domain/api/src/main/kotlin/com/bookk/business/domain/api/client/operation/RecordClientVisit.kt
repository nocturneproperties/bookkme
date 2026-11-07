package com.bookk.business.domain.api.client.operation

import kotlin.time.Instant
import kotlin.uuid.Uuid

interface RecordClientVisit {
    suspend operator fun invoke(businessId: Uuid, clientId: Uuid, visitEnd: Instant): Result<Unit>
}
