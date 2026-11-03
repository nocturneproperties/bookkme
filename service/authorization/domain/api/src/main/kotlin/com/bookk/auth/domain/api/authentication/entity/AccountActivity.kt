package com.bookk.auth.domain.api.authentication.entity

import kotlin.time.Instant
import kotlin.uuid.Uuid

data class AccountActivity(
    val authId: Uuid,
    val createdAt: Instant,
    val lastActiveAt: Instant,
) {
    companion object {
        fun stub(
            authId: Uuid = Uuid.random(),
            createdAt: Instant = Instant.fromEpochMilliseconds(0),
            lastActiveAt: Instant = Instant.fromEpochMilliseconds(0),
        ) = AccountActivity(authId, createdAt, lastActiveAt)
    }
}
