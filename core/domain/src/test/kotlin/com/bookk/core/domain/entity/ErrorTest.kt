package com.bookk.core.domain.entity

import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import kotlin.coroutines.cancellation.CancellationException

internal class ErrorTest {

    private val cancellation = CancellationException("cancelled")

    @Test
    fun `should rethrow cancellation thrown by the constraint failure action`() = runUnitTest {
        given()
        val result = Result.failure<Unit>(Error.UniqueConstraintFailed("duplicate", IllegalStateException()))

        whenn()
        val thrown = runCatching { result.onConstraintFailure { throw cancellation } }

        then()
        assertSame(cancellation, thrown.exceptionOrNull())
    }

    @Test
    fun `should rethrow cancellation thrown by the permissions missing action`() = runUnitTest {
        given()
        val result = Result.failure<Unit>(Error.OperationNotAllowed())

        whenn()
        val thrown = runCatching { result.onPermissionsMissing { throw cancellation } }

        then()
        assertSame(cancellation, thrown.exceptionOrNull())
    }

    @Test
    fun `should rethrow cancellation thrown by the business failure action`() = runUnitTest {
        given()
        val result = Result.failure<Unit>(BusinessError(statusCode = 422, code = 1, message = "failure"))

        whenn()
        val thrown = runCatching { result.onBusinessFailure { throw cancellation } }

        then()
        assertSame(cancellation, thrown.exceptionOrNull())
    }

    @Test
    fun `should rethrow cancellation thrown by the permissions missing return action`() = runUnitTest {
        given()
        val result = Result.failure<Unit>(Error.OperationNotAllowed())

        whenn()
        val thrown = runCatching { result.onPermissionsMissingReturn { throw cancellation } }

        then()
        assertSame(cancellation, thrown.exceptionOrNull())
    }
}
