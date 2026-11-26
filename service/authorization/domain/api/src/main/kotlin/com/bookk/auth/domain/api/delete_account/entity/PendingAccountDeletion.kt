package com.bookk.auth.domain.api.delete_account.entity

import kotlin.time.Instant
import kotlin.uuid.Uuid

data class PendingAccountDeletion(
    val authId: Uuid,
    val userId: Uuid,
    val deletesAt: Instant,
    val reminder: DeletionReminder,
    val deletionHold: Boolean,
) {
    companion object {
        fun stub(
            authId: Uuid = Uuid.random(),
            userId: Uuid = Uuid.random(),
            deletesAt: Instant = Instant.fromEpochMilliseconds(0),
            reminder: DeletionReminder = DeletionReminder.NONE,
            deletionHold: Boolean = false,
        ): PendingAccountDeletion {
            return PendingAccountDeletion(authId, userId, deletesAt, reminder, deletionHold)
        }
    }
}

enum class DeletionReminder {
    NONE,
    TWO_DAYS,
    ONE_DAY,
}
