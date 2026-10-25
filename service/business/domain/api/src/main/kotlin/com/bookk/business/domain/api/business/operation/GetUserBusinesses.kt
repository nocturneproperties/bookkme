package com.bookk.business.domain.api.business.operation

import com.bookk.business.domain.api.business.entity.UserBusinesses
import kotlin.uuid.Uuid

interface GetUserBusinesses {
    suspend operator fun invoke(userId: Uuid): Result<UserBusinesses>
}