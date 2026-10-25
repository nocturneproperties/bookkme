package com.bookk.core.data

import com.bookk.core.domain.entity.Error
import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.hours

internal class DataSourceTest {

    private class SutFixture {
        val sut = object : DataSource() {}
    }

    @Test
    fun `should map an unexpected exception to an unknown error`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val thrown = runCatching { fixture.sut.dbQuery { throw IllegalStateException("failure") } }.exceptionOrNull()

        then()
        assertTrue(thrown is Error.UnknownError)
    }

    @Test
    fun `should map a query timeout to an unknown error`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val thrown = runCatching { fixture.sut.dbQuery { delay(1.hours) } }.exceptionOrNull()

        then()
        assertTrue(thrown is Error.UnknownError)
    }

    @Test
    fun `should rethrow cancellation of the calling coroutine instead of mapping it`() = runUnitTest {
        given()
        val fixture = SutFixture()
        val thrown = CompletableDeferred<Throwable>()
        val job = launch {
            runCatching { fixture.sut.dbQuery { awaitCancellation() } }.onFailure { thrown.complete(it) }
        }
        runCurrent()

        whenn()
        job.cancelAndJoin()

        then()
        assertTrue(thrown.await() is CancellationException)
    }
}
