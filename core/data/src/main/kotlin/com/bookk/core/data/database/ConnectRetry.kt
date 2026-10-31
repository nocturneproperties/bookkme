package com.bookk.core.data.database

import org.slf4j.LoggerFactory
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal fun <T> retryWhileDatabaseUnreachable(
    sleep: (Duration) -> Unit = { Thread.sleep(it.inWholeMilliseconds) },
    connect: () -> T
): T {
    val delays = connectRetryDelays().iterator()
    while (true) {
        try {
            return connect()
        } catch (failure: Exception) {
            if (!failure.isDatabaseUnreachable() || !delays.hasNext()) throw failure
            val delay = delays.next()
            logger.warn("Database unreachable (${failure.rootCause().message}), retrying in $delay")
            sleep(delay)
        }
    }
}

private fun connectRetryDelays(): Sequence<Duration> =
    generateSequence(FIRST_CONNECT_RETRY_DELAY) { minOf(it * 2, LONGEST_CONNECT_RETRY_DELAY) }
        .take(CONNECT_RETRIES)

private fun Throwable.isDatabaseUnreachable(): Boolean = causes().any { cause ->
    cause is UnknownHostException || cause is SocketException || cause is SocketTimeoutException
}

private fun Throwable.rootCause(): Throwable = causes().last()

private fun Throwable.causes(): Sequence<Throwable> =
    generateSequence(this) { current -> current.cause?.takeIf { it !== current } }

private val logger = LoggerFactory.getLogger("com.bookk.core.data.database.Migration")

private const val CONNECT_RETRIES = 20

private val FIRST_CONNECT_RETRY_DELAY = 1.seconds

private val LONGEST_CONNECT_RETRY_DELAY = 10.seconds
