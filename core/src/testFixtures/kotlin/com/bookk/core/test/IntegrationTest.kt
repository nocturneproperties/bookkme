package com.bookk.core.test

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

fun runIntegrationTest(
    context: CoroutineContext = TestHolder(),
    timeout: Duration = 30.seconds,
    body: suspend TestScope.() -> Unit
) = runTest(context, timeout = timeout) {
    body()
    requireNotNull(currentCoroutineContext()[TestHolder.Key]).assertFormat()
}
