package com.bookk.core.domain.entity

import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.coroutines.cancellation.CancellationException

internal class RunCatchingUnlessCancelledTest {

    @Test
    fun `should return success holding the block value`() = runUnitTest {
        given()
        val value = "value"

        whenn()
        val result = runCatchingUnlessCancelled { value }

        then()
        assertEquals(value, result.getOrNull())
    }

    @Test
    fun `should return failure holding the thrown exception`() = runUnitTest {
        given()
        val failure = IllegalStateException("failure")

        whenn()
        val result = runCatchingUnlessCancelled { throw failure }

        then()
        assertSame(failure, result.exceptionOrNull())
    }

    @Test
    fun `should return failure for a cancellation the block throws while the caller is active`() = runUnitTest {
        given()
        val strayCancellation = CancellationException("stray")

        whenn()
        val result = runCatchingUnlessCancelled { throw strayCancellation }

        then()
        assertSame(strayCancellation, result.exceptionOrNull())
    }

    @Test
    fun `should rethrow when the calling coroutine itself is cancelled`() = runUnitTest {
        given()
        var returnedResult = false
        var thrown: Throwable? = null

        whenn()
        val job = launch {
            try {
                runCatchingUnlessCancelled {
                    cancel()
                    throw CancellationException("cancelled")
                }
                returnedResult = true
            } catch (e: CancellationException) {
                thrown = e
                throw e
            }
        }
        job.join()

        then()
        assertFalse(returnedResult)
        assertTrue(thrown is CancellationException)
        assertTrue(job.isCancelled)
    }

    @Test
    fun `should stop cancelled coroutine instead of resuming after the block`() = runUnitTest {
        given()
        var resumedAfterCancellation = false
        val job = launch {
            runCatchingUnlessCancelled { awaitCancellation() }
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
