package com.bookk.core.coroutines

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration

fun ticks(interval: Duration): Flow<Unit> = flow {
    while (true) {
        emit(Unit)
        delay(interval)
    }
}
