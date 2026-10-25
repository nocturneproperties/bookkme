package com.bookk.core.domain.entity

import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.coroutines.cancellation.CancellationException

internal class RunSuspendCatchingTest {

    @Test
    fun `should return success holding the block value`() = runUnitTest {
        given()
        val value = "value"

        whenn()
        val result = runSuspendCatching { value }

        then()
        assertEquals(value, result.getOrNull())
    }

    @Test
    fun `should return failure holding the thrown exception`() = runUnitTest {
        given()
        val failure = IllegalStateException("failure")

        whenn()
        val result = runSuspendCatching { throw failure }

        then()
        assertSame(failure, result.exceptionOrNull())
    }

    @Test
    fun `should rethrow cancellation exception instead of returning failure`() = runUnitTest {
        given()
        val cancellation = CancellationException("cancelled")

        whenn()
        val thrown = runCatching { runSuspendCatching { throw cancellation } }

        then()
        assertSame(cancellation, thrown.exceptionOrNull())
    }

    @Test
    fun `should stop cancelled coroutine instead of resuming after the block`() = runUnitTest {
        given()
        var resumedAfterCancellation = false
        val job = launch {
            runSuspendCatching { awaitCancellation() }
            resumedAfterCancellation = true
        }
        runCurrent()

        whenn()
        job.cancelAndJoin()

        then()
        assertTrue(job.isCancelled)
        assertFalse(resumedAfterCancellation)
    }
}
