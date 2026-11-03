package com.bookk.core.domain.entity

import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds

internal class RunCleaningUpOnFailureTest {

    @Test
    fun `should return the block value without cleaning up`() = runUnitTest {
        given()
        var cleanedUp = false

        whenn()
        val value = runCleaningUpOnFailure(cleanUp = { cleanedUp = true }) { "value" }

        then()
        assertEquals("value", value)
        assertEquals(false, cleanedUp)
    }

    @Test
    fun `should clean up and rethrow the block's failure`() = runUnitTest {
        given()
        val failure = IllegalStateException("failure")
        var cleanedUp = false

        whenn()
        val thrown = runCatching { runCleaningUpOnFailure(cleanUp = { cleanedUp = true }) { throw failure } }.exceptionOrNull()

        then()
        assertSame(failure, thrown)
        assertTrue(cleanedUp)
    }

    @Test
    fun `should finish a suspending clean up when the caller is cancelled`() = runUnitTest {
        given()
        var cleanedUp = false
        var thrown: Throwable? = null
        val job = launch {
            try {
                runCleaningUpOnFailure(cleanUp = {
                    delay(1.seconds)
                    cleanedUp = true
                }) { awaitCancellation() }
            } catch (failure: Throwable) {
                thrown = failure
                throw failure
            }
        }
        runCurrent()

        whenn()
        job.cancelAndJoin()

        then()
        assertTrue(cleanedUp)
        assertTrue(thrown is CancellationException)
    }

    @Test
    fun `should keep the block's failure when the clean up fails too`() = runUnitTest {
        given()
        val failure = IllegalStateException("failure")
        val cleanUpFailure = IllegalArgumentException("clean up failed")

        whenn()
        val thrown = runCatching { runCleaningUpOnFailure(cleanUp = { throw cleanUpFailure }) { throw failure } }.exceptionOrNull()

        then()
        assertSame(failure, thrown)
        val suppressed = failure.suppressedExceptions.single()
        assertTrue(suppressed is IllegalArgumentException)
        assertEquals(cleanUpFailure.message, suppressed.message)
    }
}
