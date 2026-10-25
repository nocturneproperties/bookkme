package com.bookk.core.domain.entity

import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import kotlin.coroutines.cancellation.CancellationException

internal class RecoverSuspendCatchingTest {

    @Test
    fun `should keep success untouched`() = runUnitTest {
        given()
        val result = Result.success("value")

        whenn()
        val recovered = result.recoverSuspendCatching { "recovered" }

        then()
        assertEquals("value", recovered.getOrNull())
    }

    @Test
    fun `should return success holding the recovered value`() = runUnitTest {
        given()
        val result = Result.failure<String>(IllegalStateException("failure"))

        whenn()
        val recovered = result.recoverSuspendCatching { "recovered" }

        then()
        assertEquals("recovered", recovered.getOrNull())
    }

    @Test
    fun `should return failure holding the exception thrown by the transform`() = runUnitTest {
        given()
        val result = Result.failure<String>(IllegalStateException("failure"))
        val mapped = IllegalArgumentException("mapped")

        whenn()
        val recovered = result.recoverSuspendCatching { throw mapped }

        then()
        assertSame(mapped, recovered.exceptionOrNull())
    }

    @Test
    fun `should rethrow cancellation thrown by the transform instead of returning failure`() = runUnitTest {
        given()
        val result = Result.failure<String>(IllegalStateException("failure"))
        val cancellation = CancellationException("cancelled")

        whenn()
        val thrown = runCatching { result.recoverSuspendCatching { throw cancellation } }

        then()
        assertSame(cancellation, thrown.exceptionOrNull())
    }
}
