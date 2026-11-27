package com.bookk.business.domain.api.user.entity

import kotlin.uuid.Uuid

data class UserLimits(val userId: Uuid, val maxBusinesses: Int) {
    companion object {
        const val DEFAULT_MAX_BUSINESSES = 20
        const val DEMO_MAX_BUSINESSES = 1

        fun stub(userId: Uuid = Uuid.random(), maxBusinesses: Int = DEFAULT_MAX_BUSINESSES): UserLimits {
            return UserLimits(userId, maxBusinesses)
        }
    }
}
