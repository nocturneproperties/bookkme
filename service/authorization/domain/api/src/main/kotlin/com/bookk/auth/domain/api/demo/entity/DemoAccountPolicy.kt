package com.bookk.auth.domain.api.demo.entity

import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

object DemoAccountPolicy {
    val LENGTH = 14.days
    val UPGRADE_GRACE_PERIOD = 7.days
    const val MAX_ACTIVE_ACCOUNTS = 1000

    fun upgradeDeadline(demoExpiresAt: Instant): Instant {
        return demoExpiresAt + UPGRADE_GRACE_PERIOD
    }
}
