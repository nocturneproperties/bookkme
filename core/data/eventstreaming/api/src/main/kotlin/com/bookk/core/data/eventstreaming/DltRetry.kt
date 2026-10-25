package com.bookk.core.data.eventstreaming

object DltRetry {
    const val DEFAULT_MAX_ATTEMPTS = 3
    private const val BASE_BACKOFF_MILLIS = 200L

    fun dltTopic(topic: String, consumerGroup: String): String = "${topic}_${consumerGroup}_dlt"

    fun isExhausted(attempt: Int, maxAttempts: Int): Boolean = attempt >= maxAttempts

    fun backoffMillis(attempt: Int): Long = BASE_BACKOFF_MILLIS * (1L shl attempt.coerceAtMost(10))
}
