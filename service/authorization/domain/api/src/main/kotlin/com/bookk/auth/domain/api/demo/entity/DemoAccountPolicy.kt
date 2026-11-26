package com.bookk.auth.domain.api.demo.entity

import kotlin.time.Duration.Companion.days

object DemoAccountPolicy {
    val LENGTH = 30.days
    val UPGRADE_GRACE_PERIOD = 7.days
    const val MAX_ACTIVE_ACCOUNTS = 1000
}
