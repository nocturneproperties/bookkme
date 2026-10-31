package com.bookk.core.data.database

import com.bookk.core.test.given
import com.bookk.core.test.runUnitTest
import com.bookk.core.test.then
import com.bookk.core.test.whenn
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.sql.SQLException
import java.sql.SQLNonTransientConnectionException
import javax.net.ssl.SSLHandshakeException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

internal class ConnectRetryTest {

    private val mariaDbRestartWithFullBufferPool = 2.minutes
    private val longestAcceptableWaitAfterDatabaseIsBack = 10.seconds

    private class SutFixture(failures: List<Throwable> = emptyList()) {
        val sleeps = mutableListOf<Duration>()
        val remainingFailures = ArrayDeque(failures)
        var attempts = 0

        fun connect(): Result<String> = runCatching {
            retryWhileDatabaseUnreachable(sleep = { sleeps += it }) {
                attempts++
                remainingFailures.removeFirstOrNull()?.let { throw it }
                "connected"
            }
        }
    }

    private fun unreachable(cause: Throwable) = RuntimeException(
        "Unable to obtain connection",
        SQLNonTransientConnectionException("Socket fail to connect", "08000", cause)
    )

    @Test
    fun `should connect on the first attempt without waiting`() = runUnitTest {
        given()
        val fixture = SutFixture()

        whenn()
        val result = fixture.connect()

        then()
        assertEquals("connected", result.getOrNull())
        assertEquals(emptyList<Duration>(), fixture.sleeps)
    }

    @Test
    fun `should retry while the database host does not resolve`() = runUnitTest {
        given()
        val fixture = SutFixture(failures = List(3) { unreachable(UnknownHostException("monolith.database")) })

        whenn()
        val result = fixture.connect()

        then()
        assertEquals("connected", result.getOrNull())
        assertEquals(4, fixture.attempts)
        assertEquals(listOf(1.seconds, 2.seconds, 4.seconds), fixture.sleeps)
    }

    @Test
    fun `should retry while the database refuses or drops connections`() = runUnitTest {
        given()
        val fixture = SutFixture(
            failures = listOf(
                unreachable(ConnectException("Connection refused")),
                unreachable(SocketException("Connection reset")),
                unreachable(SocketTimeoutException("Connect timed out"))
            )
        )

        whenn()
        val result = fixture.connect()

        then()
        assertEquals("connected", result.getOrNull())
        assertEquals(4, fixture.attempts)
    }

    @Test
    fun `should fail at once when the database rejects the login`() = runUnitTest {
        given()
        val rejected = SQLException("Access denied for user 'monolith'", "28000", 1045)
        val fixture = SutFixture(failures = listOf(rejected))

        whenn()
        val result = fixture.connect()

        then()
        assertSame(rejected, result.exceptionOrNull())
        assertEquals(1, fixture.attempts)
        assertEquals(emptyList<Duration>(), fixture.sleeps)
    }

    @Test
    fun `should fail at once when the server certificate cannot be verified`() = runUnitTest {
        given()
        val rejected = RuntimeException(SSLHandshakeException("certificate verification failed"))
        val fixture = SutFixture(failures = listOf(rejected))

        whenn()
        val result = fixture.connect()

        then()
        assertSame(rejected, result.exceptionOrNull())
        assertEquals(1, fixture.attempts)
    }

    @Test
    fun `should give up with the last failure once the retries are spent`() = runUnitTest {
        given()
        val failures = List(100) { unreachable(UnknownHostException("monolith.database")) }
        val fixture = SutFixture(failures = failures)

        whenn()
        val result = fixture.connect()

        then()
        assertSame(failures[fixture.attempts - 1], result.exceptionOrNull())
        assertEquals(fixture.sleeps.size + 1, fixture.attempts)
    }

    @Test
    fun `should keep retrying for longer than a database restart takes`() = runUnitTest {
        given()
        val fixture = SutFixture(failures = List(100) { unreachable(UnknownHostException("monolith.database")) })

        whenn()
        fixture.connect()

        then()
        assertTrue(fixture.sleeps.fold(Duration.ZERO, Duration::plus) > mariaDbRestartWithFullBufferPool)
    }

    @Test
    fun `should retry often enough to connect soon after the database is back`() = runUnitTest {
        given()
        val fixture = SutFixture(failures = List(100) { unreachable(UnknownHostException("monolith.database")) })

        whenn()
        fixture.connect()

        then()
        assertTrue(fixture.sleeps.all { it <= longestAcceptableWaitAfterDatabaseIsBack })
    }
}
