package com.bookk.auth.domain.api.authentication.entity

import kotlin.time.Instant
import kotlin.uuid.Uuid

data class DemoAccount(
    val authId: Uuid,
    val userId: Uuid,
    val expiresAt: Instant,
    val deletionHold: Boolean,
) {
    companion object {
        fun stub(
            authId: Uuid = Uuid.random(),
            userId: Uuid = Uuid.random(),
            expiresAt: Instant = Instant.fromEpochMilliseconds(0),
            deletionHold: Boolean = false,
        ): DemoAccount {
            return DemoAccount(authId, userId, expiresAt, deletionHold)
        }
    }
}
