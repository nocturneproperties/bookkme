package com.bookk.business.domain.api.employee.operation

import kotlin.uuid.Uuid

interface GetOwnedBusinessMemberIds {
    suspend operator fun invoke(ownerUserId: Uuid): Result<List<Uuid>>
}
