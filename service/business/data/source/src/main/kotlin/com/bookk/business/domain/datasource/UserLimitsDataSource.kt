package com.bookk.business.domain.datasource

import com.bookk.business.domain.api.user.entity.UserLimits
import kotlin.uuid.Uuid

interface UserLimitsDataSource {
    suspend fun lockForUser(userId: Uuid): UserLimits
    suspend fun deleteForUser(userId: Uuid)
}
