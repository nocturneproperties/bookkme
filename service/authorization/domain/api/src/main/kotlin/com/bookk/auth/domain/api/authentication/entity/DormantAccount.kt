package com.bookk.auth.domain.api.authentication.entity

import kotlin.time.Instant
import kotlin.uuid.Uuid

data class DormantAccount(
    val authId: Uuid,
    val userId: Uuid,
    val lastActiveAt: Instant,
    val stage: DormancyStage,
    val warnedAt: Instant?,
) {
    companion object {
        fun stub(
            authId: Uuid = Uuid.random(),
            userId: Uuid = Uuid.random(),
            lastActiveAt: Instant = Instant.fromEpochMilliseconds(0),
            stage: DormancyStage = DormancyStage.ACTIVE,
            warnedAt: Instant? = null,
        ) = DormantAccount(authId, userId, lastActiveAt, stage, warnedAt)
    }
}

enum class DormancyStage {
    ACTIVE,
    SENT_60_DAY_WARNING,
    SENT_30_DAY_WARNING,
    SENT_7_DAY_WARNING,
}
