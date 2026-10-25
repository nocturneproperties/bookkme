package com.bookk.core.data.eventstreaming.impl

import com.bookk.core.data.cache.test.InMemoryCacheClient
import com.bookk.core.data.eventstreaming.EventIdempotencyStorage
import com.bookk.core.data.repository.CacheEventIdempotencyStorage
import kotlin.time.TimeSource

fun inMemoryEventIdempotencyStorage(timeSource: TimeSource = TimeSource.Monotonic): EventIdempotencyStorage =
    CacheEventIdempotencyStorage(InMemoryCacheClient(timeSource = timeSource))
