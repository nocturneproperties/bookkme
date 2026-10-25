package com.bookk.core.test

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.seconds

internal class TestContext(
    var givenCalled: Boolean = false,
    var whenCalled: Boolean = false,
    var thenCalled: Boolean = false
)


class TestHolder : AbstractCoroutineContextElement(Key) {
    private val context = TestContext()

    fun givenCalled() {
        context.givenCalled = true
    }

    fun whenCalled() {
        context.whenCalled = true
    }

    fun thenCalled() {
        context.thenCalled = true
    }

    fun assertFormat() {
        assert(context.givenCalled) { "Given step is missing" }
        assert(context.whenCalled) { "When step is missing" }
        assert(context.thenCalled) { "Then step is missing" }
    }

    companion object Key : CoroutineContext.Key<TestHolder>
}

fun runUnitTest(context: CoroutineContext = TestHolder(), body: suspend TestScope.() -> Unit) =
    runTest(context, timeout = 30.seconds) {
        body()
        requireNotNull(currentCoroutineContext()[TestHolder.Key]).assertFormat()
    }

suspend fun given() {
    requireNotNull(currentCoroutineContext()[TestHolder.Key]).givenCalled()
}

suspend fun whenn() {
    requireNotNull(currentCoroutineContext()[TestHolder.Key]).whenCalled()
}

suspend fun then() {
    requireNotNull(currentCoroutineContext()[TestHolder.Key]).thenCalled()
}
