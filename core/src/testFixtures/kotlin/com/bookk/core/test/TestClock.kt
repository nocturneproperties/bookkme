package com.bookk.core.test

import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

class TestClock(var now: Instant = Instant.fromEpochMilliseconds(1_800_000_000_000)) : Clock {
    override fun now(): Instant = now

    operator fun plusAssign(duration: Duration) {
        now += duration
    }
}
