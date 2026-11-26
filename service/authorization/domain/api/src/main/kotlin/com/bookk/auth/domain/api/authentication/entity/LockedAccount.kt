package com.bookk.auth.domain.api.authentication.entity

import kotlin.uuid.Uuid

data class LockedAccount(
    val id: Uuid,
    val deletionHold: Boolean,
) {
    companion object {
        fun stub(id: Uuid = Uuid.random(), deletionHold: Boolean = false): LockedAccount {
            return LockedAccount(id, deletionHold)
        }
    }
}
