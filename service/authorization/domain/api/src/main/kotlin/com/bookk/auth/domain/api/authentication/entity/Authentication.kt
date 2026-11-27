package com.bookk.auth.domain.api.authentication.entity

import kotlin.time.Instant
import kotlin.uuid.Uuid

data class Authentication(
    val id: Uuid,
    val userId: Uuid,
    val uuid: Uuid,
    val demoExpiresAt: Instant? = null,
) {
    val isDemo: Boolean get() = demoExpiresAt != null

    companion object {
        fun stub(
            id: Uuid = Uuid.random(),
            userId: Uuid = Uuid.random(),
            uuid: Uuid = Uuid.random(),
            demoExpiresAt: Instant? = null,
        ): Authentication {
            return Authentication(id, userId, uuid, demoExpiresAt)
        }
    }
}
