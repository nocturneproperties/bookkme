package com.bookk.core.domain.entity

import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class ThrowIfAnyFailedTest {

    @Test
    fun `should not throw when every result succeeded`() = runUnitTest {
        given()
        val results = listOf(Result.success(1), Result.success(2))

        whenn()
        val thrown = runCatching { results.throwIfAnyFailed() }

        then()
        assertTrue(thrown.isSuccess)
    }

    @Test
    fun `should not throw for no results`() = runUnitTest {
        given()
        val results = emptyList<Result<Unit>>()

        whenn()
        val thrown = runCatching { results.throwIfAnyFailed() }

        then()
        assertTrue(thrown.isSuccess)
    }

    @Test
    fun `should throw the first failure with the later ones suppressed`() = runUnitTest {
        given()
        val first = IllegalStateException("first")
        val second = IllegalArgumentException("second")
        val third = IllegalStateException("third")
        val results = listOf(
            Result.success(Unit),
            Result.failure(first),
            Result.success(Unit),
            Result.failure(second),
            Result.failure(third),
        )

        whenn()
        val thrown = runCatching { results.throwIfAnyFailed() }.exceptionOrNull()

        then()
        assertSame(first, thrown)
        assertEquals(listOf(second, third), first.suppressed.toList())
    }
}
